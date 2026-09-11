/**
 * 智能滚动辅助：仅当用户停留在底部附近时才自动跟随新内容，
 * 用户上滑阅读历史时不打断其滚动位置。
 */
const STICK_THRESHOLD = 80

export class AutoScroll {
  constructor() {
    this.shouldStick = true
  }

  /** 依据容器当前滚动位置更新「是否应贴底」状态 */
  update(el) {
    if (!el) return
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight
    this.shouldStick = distance <= STICK_THRESHOLD
  }
}
