#!/usr/bin/env node
/**
 * 轻量 .vue 组件校验器（只编译、不产出文件）
 *
 * 为什么不直接 `npm run build`：
 * 构建会先清空 dist 目录，容易被宿主的安全策略拦下；而这个脚本只做
 * 「单文件能不能编译」这一件事，失败时能精确定位到文件与行号，反馈更快。
 *
 * 用法：
 *   node scripts/check-sfc.mjs                 # 校验 src 下全部 .vue
 *   node scripts/check-sfc.mjs src/views       # 校验指定目录
 *   node scripts/check-sfc.mjs src/App.vue     # 校验指定文件
 */

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse, compileScript, compileTemplate, compileStyle } from '@vue/compiler-sfc'

const projectRoot = fileURLToPath(new URL('../', import.meta.url))

/** 递归收集 .vue 文件 */
function collectVueFiles(target, out = []) {
  const stat = statSync(target, { throwIfNoEntry: false })
  if (!stat) return out
  if (stat.isFile()) {
    if (target.endsWith('.vue')) out.push(target)
    return out
  }
  for (const entry of readdirSync(target)) {
    if (entry === 'node_modules' || entry === 'dist') continue
    collectVueFiles(join(target, entry), out)
  }
  return out
}

function checkFile(file) {
  const source = readFileSync(file, 'utf8')
  const id = relative(projectRoot, file).replace(/[\\/]/g, '_')
  const rel = relative(projectRoot, file)

  const { descriptor, errors } = parse(source, { filename: file })
  const problems = (errors || []).map((e) => `模板/解析: ${e.message}`)

  if (!problems.length) {
    try {
      // 有 <script setup> 时编译 setup；否则退回普通 script
      compileScript(descriptor, { id })
    } catch (e) {
      problems.push(`script: ${e.message}`)
    }
  }

  if (!problems.length) {
    try {
      const result = compileTemplate({
        source: descriptor.template?.content ?? '',
        filename: file,
        id
      })
      for (const e of result.errors || []) problems.push(`template: ${e.message || e}`)
    } catch (e) {
      problems.push(`template: ${e.message}`)
    }
  }

  if (!problems.length) {
    descriptor.styles.forEach((style, index) => {
      try {
        const result = compileStyle({
          source: style.content,
          filename: file,
          id,
          scoped: Boolean(style.scoped)
        })
        for (const e of result.errors || []) problems.push(`style[${index}]: ${e.message || e}`)
      } catch (e) {
        problems.push(`style[${index}]: ${e.message}`)
      }
    })
  }

  return { rel, problems }
}

const args = process.argv.slice(2)
const targets = args.length ? args.map((a) => resolve(process.cwd(), a)) : [join(projectRoot, 'src')]

const files = targets.flatMap((t) => collectVueFiles(t))
if (!files.length) {
  console.error('没有找到需要校验的 .vue 文件')
  process.exit(2)
}

let failed = 0
for (const file of files.sort()) {
  const { rel, problems } = checkFile(file)
  if (problems.length) {
    failed += 1
    console.error(`\n✗ ${rel}`)
    for (const p of problems) console.error(`    ${p}`)
  }
}

if (failed) {
  console.error(`\n共 ${files.length} 个组件，${failed} 个编译失败`)
  process.exit(1)
}
console.log(`✓ ${files.length} 个 .vue 组件全部编译通过`)
