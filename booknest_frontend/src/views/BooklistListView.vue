<script setup>
/**
 * 书单广场：公开书单列表 + 「我的书单」切换
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as booklistApi from '@/api/booklist'
import { fromNow } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import BnAvatar from '@/components/BnAvatar.vue'
import BnCover from '@/components/BnCover.vue'

const router = useRouter()
const userStore = useUserStore()

const booklists = ref([])
const loading = ref(false)
const error = ref('')
const tab = ref('public')

const page = ref(1)
const PAGE_SIZE = 12
const finished = ref(false)

const isMine = computed(() => tab.value === 'mine')

async function load(reset = false) {
  if (!userStore.isLoggedIn && isMine.value) {
    booklists.value = []
    return
  }
  if (reset) {
    page.value = 1
    finished.value = false
    error.value = ''
  }
  if (finished.value) return

  loading.value = true
  try {
    const data = isMine.value
      ? await booklistApi.listMyBooklists()
      : await booklistApi.listBooklists({ page: page.value, pageSize: PAGE_SIZE })

    const list = data || []
    if (isMine.value) {
      booklists.value = list
      finished.value = true
    } else {
      booklists.value = reset ? list : booklists.value.concat(list)
      if (list.length < PAGE_SIZE) finished.value = true
      else page.value += 1
    }
  } catch (err) {
    error.value = err.message || '书单加载失败'
    finished.value = true
  } finally {
    loading.value = false
  }
}

function goCreate() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: '/booklist/create' } })
    return
  }
  router.push({ name: 'booklist-create' })
}

watch(tab, () => load(true))
onMounted(() => load(true))
</script>

<template>
  <div class="bn-container">
    <div class="head">
      <div>
        <h1 class="bn-page-title">书单广场</h1>
        <p class="bn-page-sub">把喜欢的书整理成一份自己的书单</p>
      </div>
      <el-button type="primary" @click="goCreate">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>创建书单
      </el-button>
    </div>

    <el-radio-group v-model="tab" class="tabs">
      <el-radio-button value="public">公开书单</el-radio-button>
      <el-radio-button value="mine">我的书单</el-radio-button>
    </el-radio-group>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="bn-mt-12" />

    <el-skeleton v-if="loading && !booklists.length" class="bn-mt-16" :rows="4" animated />

    <template v-else>
      <div v-if="booklists.length" class="grid">
        <router-link
          v-for="item in booklists"
          :key="item.id"
          :to="`/booklist/${item.id}`"
          class="bn-card bn-card-hover list-card"
        >
          <BnCover :src="item.coverImage" :title="item.title" height="126px" />

          <div class="list-body">
            <div class="list-top">
              <p class="list-title bn-ellipsis-1" :title="item.title">{{ item.title }}</p>
              <el-tag v-if="item.visibility === 0" size="small" type="info" effect="plain">
                私密
              </el-tag>
            </div>

            <p class="list-summary bn-ellipsis-2">
              {{ item.summary || '这份书单还没有简介' }}
            </p>

            <div class="list-stats">
              <span>{{ item.bookCount || 0 }} 本</span>
              <span>·</span>
              <span>{{ item.likeCount || 0 }} 赞</span>
              <span>·</span>
              <span>{{ item.collectCount || 0 }} 收藏</span>
            </div>

            <div class="list-footer">
              <div class="bn-row bn-gap-8">
                <BnAvatar
                  :src="item.userAvatar"
                  :name="item.userName"
                  :size="22"
                  :linkable="false"
                />
                <span class="owner bn-ellipsis-1">{{ item.userName || '书友' }}</span>
              </div>
              <span class="bn-text-muted time">{{ fromNow(item.createTime) }}</span>
            </div>
          </div>
        </router-link>
      </div>

      <div v-else class="bn-empty">
        <el-icon :size="34" color="#c8bdb1"><Collection /></el-icon>
        <p class="bn-mt-12">
          {{ isMine ? '你还没有创建书单' : '还没有公开书单' }}
        </p>
        <el-button v-if="isMine" type="primary" class="bn-mt-12" @click="goCreate">
          创建第一份书单
        </el-button>
      </div>

      <div v-if="!isMine && booklists.length && !finished" class="load-more">
        <el-button text :loading="loading" @click="load(false)">加载更多</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.head .bn-page-sub {
  margin-bottom: 0;
}

.tabs {
  margin: 16px 0 18px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(224px, 1fr));
  gap: 16px;
}

.list-card {
  padding: 12px;
  display: block;
}

.list-body {
  margin-top: 10px;
}

.list-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.list-title {
  font-size: 14.5px;
  font-weight: 600;
  flex: 1;
  min-width: 0;
}

.list-summary {
  font-size: 12.5px;
  color: var(--bn-text-sub);
  line-height: 1.6;
  margin-top: 5px;
  min-height: 40px;
}

.list-stats {
  display: flex;
  gap: 5px;
  font-size: 12px;
  color: var(--bn-text-muted);
  margin-top: 7px;
}

.list-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px solid #f4f0ec;
}

.owner {
  font-size: 12.5px;
  color: var(--bn-text-sub);
  max-width: 90px;
}

.time {
  font-size: 11.5px;
}

.load-more {
  text-align: center;
  padding: 22px 0;
}
</style>
