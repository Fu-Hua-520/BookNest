<script setup>
/**
 * 聊天面板（私信 + 群聊）
 *
 * 同一份实现服务两种容器：
 *   - compact = false：左右两栏，用于独立聊天页 /chat
 *   - compact = true ：单列切换（列表 ↔ 对话），用于全局侧边栏抽屉
 * 这样私聊/群聊/图片消息/已读逻辑只维护一遍。
 */
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as chatApi from '@/api/chat'
import * as followApi from '@/api/follow'
import { uploadFile } from '@/api/user'
import { AutoScroll } from '@/utils/autoscroll'
import { fromNow } from '@/utils/format'
import { useBadgeStore } from '@/stores/badge'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import BnAvatar from '@/components/BnAvatar.vue'

const props = defineProps({
  /** 紧凑模式：列表与对话在同一列内切换（侧边栏面板用） */
  compact: { type: Boolean, default: false },
  /** 初始打开的渠道：{ type: 'PRIVATE'|'GROUP', id } */
  initial: { type: Object, default: null },
  /** 进来就弹出「创建群聊」弹窗（个人主页的建群入口用） */
  autoCreateGroup: { type: Boolean, default: false },
  /** 进来就弹出「发现群聊」弹窗（侧边栏的「加入别人的群」入口用） */
  autoDiscoverGroup: { type: Boolean, default: false },
  /** 初始停在哪个 Tab：'private' | 'group'（可空，空则沿用上次/默认私信） */
  initialTab: { type: String, default: '' }
})

/**
 * 自动弹窗类开关消费掉之后要通知父组件复位。
 * 不复位的话，父组件下次再用同一个开关打开本组件时仍会立刻弹窗。
 */
const emit = defineEmits(['group-request-consumed'])

const router = useRouter()
const userStore = useUserStore()
const badgeStore = useBadgeStore()
const chatStore = useChatStore()

/** 会话类型 Tab */
const tab = ref('private')
/** PRIVATE / GROUP */
const activeType = ref('PRIVATE')
const activeId = ref('')

const conversations = ref([])
const groups = ref([])
const messages = ref([])
const input = ref('')

const loadingConv = ref(false)
const loadingGroup = ref(false)
const loadingMsg = ref(false)
const uploading = ref(0)
/** 紧凑模式下是否处于「对话」视图（false 表示列表视图） */
const chatOpen = ref(false)
/** 对方是否在线（仅私聊有意义） */
const peerOnline = ref(false)
const showEmoji = ref(false)

const scrollerRef = ref(null)
const composerRef = ref(null)
const fileInputRef = ref(null)
const autoScroll = new AutoScroll()

let unsubscribe = null

/** 常用表情：够用就好，不做完整 emoji picker */
const EMOJIS = [
  '😀', '😄', '😁', '😊', '🙂', '😉', '😍', '🤔',
  '😂', '😅', '😭', '😢', '😡', '👍', '👏', '🙏',
  '🎉', '❤️', '🔥', '✨', '📚', '📖', '✍️', '☕'
]

const activeConv = computed(() =>
  conversations.value.find((item) => String(item.id) === activeId.value)
)
const activeGroup = computed(() => groups.value.find((item) => String(item.id) === activeId.value))

const peerId = computed(() => activeConv.value?.peerId || '')
const channelName = computed(() => {
  if (activeType.value === 'GROUP') return activeGroup.value?.name || '群聊'
  return activeConv.value?.peerName || '私信'
})
const channelAvatar = computed(() => {
  if (activeType.value === 'GROUP') return activeGroup.value?.avatar || ''
  return activeConv.value?.peerAvatar || ''
})
const channelCount = computed(() => activeGroup.value?.memberCount || 0)
/** 私聊才有「在线/离线」；群聊展示成员数 */
const channelSubtitle = computed(() =>
  activeType.value === 'GROUP'
    ? `${channelCount.value} 位成员`
    : peerOnline.value
      ? '在线'
      : '离线'
)

const list = computed(() => (tab.value === 'group' ? groups.value : conversations.value))
const listLoading = computed(() => (tab.value === 'group' ? loadingGroup.value : loadingConv.value))

/** 私聊未读 + 群未读，用于 Tab 上的小圆点 */
const groupUnreadTotal = computed(() =>
  groups.value.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0)
)
const privateUnreadTotal = computed(() =>
  conversations.value.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0)
)

/* ------------------------- 数据加载 ------------------------- */

async function loadConversations() {
  loadingConv.value = true
  try {
    conversations.value = (await chatApi.listConversations()) || []
  } catch {
    conversations.value = []
  } finally {
    loadingConv.value = false
  }
}

async function loadGroups() {
  loadingGroup.value = true
  try {
    groups.value = (await chatApi.listMyGroups()) || []
  } catch {
    groups.value = []
  } finally {
    loadingGroup.value = false
  }
}

function loadLists() {
  return Promise.all([loadConversations(), loadGroups()])
}

async function loadMessages() {
  if (!activeId.value) {
    messages.value = []
    return
  }
  loadingMsg.value = true
  try {
    messages.value = (await chatApi.listMessages(activeId.value, 1, 100)) || []
    scrollToBottom(true)
    markRead()
  } catch {
    messages.value = []
  } finally {
    loadingMsg.value = false
  }
}

async function checkOnline() {
  if (activeType.value !== 'PRIVATE' || !peerId.value) {
    peerOnline.value = false
    return
  }
  try {
    peerOnline.value = await chatApi.isUserOnline(peerId.value)
  } catch {
    peerOnline.value = false
  }
}

/** 已读：私聊走会话已读接口，群聊推进成员表的已读位点 */
function markRead() {
  if (!activeId.value) return
  const request =
    activeType.value === 'GROUP'
      ? chatApi.markGroupRead(activeId.value)
      : chatApi.markConversationRead(activeId.value)
  request
    .then(() => {
      // 本地先清零，避免用户已经看完、角标却要等下一轮轮询才消失
      if (activeType.value === 'GROUP') {
        const group = groups.value.find((item) => String(item.id) === activeId.value)
        if (group) group.unreadCount = 0
      } else {
        const conv = conversations.value.find((item) => String(item.id) === activeId.value)
        if (conv) conv.unreadCount = 0
      }
      badgeStore.refresh()
    })
    .catch(() => {})
}

/* ------------------------- 渠道切换 ------------------------- */

function openChannel(type, id) {
  if (!id) return
  const nextId = String(id)
  if (activeType.value === type && activeId.value === nextId) {
    if (props.compact) chatOpen.value = true
    return
  }
  activeType.value = type
  activeId.value = nextId
  if (props.compact) chatOpen.value = true
}

function switchTab(value) {
  tab.value = value
  if (props.compact) chatOpen.value = false
}

function backToList() {
  chatOpen.value = false
}

function goProfile() {
  if (activeType.value === 'GROUP' || !peerId.value) return
  router.push(`/user/${peerId.value}`)
}

/* ------------------------- 滚动 ------------------------- */

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = scrollerRef.value
    if (!el) return
    if (force || autoScroll.shouldStick) el.scrollTop = el.scrollHeight
  })
}

function onScroll() {
  const el = scrollerRef.value
  if (el) autoScroll.update(el)
}

/* ------------------------- 发送 ------------------------- */

/** 统一出帧：先判条件，再乐观追加本地气泡 */
function dispatch(content, msgType) {
  if (!content) return
  if (!activeId.value) {
    ElMessage.info('请先选择一个会话')
    return
  }
  if (activeType.value === 'PRIVATE' && !peerId.value) {
    ElMessage.warning('会话信息不完整，请从书友主页重新发起私信')
    return
  }
  if (!chatStore.socketReady) {
    ElMessage.warning('实时连接尚未建立，请稍后重试')
    return
  }

  const payload =
    activeType.value === 'GROUP'
      ? { type: 'GROUP', groupId: activeId.value, content, msgType }
      : { type: 'CHAT', conversationId: activeId.value, receiverId: peerId.value, content, msgType }

  if (!chatStore.send(payload)) {
    ElMessage.warning('消息发送失败，请稍后重试')
    return
  }

  // 乐观追加：id 用 local- 前缀标记，收到 ACK 时按内容替换为真实消息。
  // 不这么做的话，等服务端回执再渲染会有明显延迟感。
  messages.value.push({
    id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    conversationId: activeId.value,
    groupId: activeType.value === 'GROUP' ? activeId.value : null,
    senderId: userStore.userId,
    senderName: userStore.username,
    senderAvatar: userStore.avatar,
    content,
    msgType,
    isRead: 0,
    createTime: new Date().toISOString(),
    isMine: true
  })
  scrollToBottom(true)
}

function send() {
  const content = input.value.trim()
  if (!content) return
  dispatch(content, 'TEXT')
  input.value = ''
  showEmoji.value = false
}

function pickEmoji(emoji) {
  input.value = `${input.value}${emoji}`
  showEmoji.value = false
  nextTick(() => composerRef.value?.focus())
}

function triggerImagePick() {
  fileInputRef.value?.click()
}

function onFilePicked(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  files.forEach(sendImage)
}

/** 图片消息：先传 OSS 拿到 URL，再把 URL 当消息内容发出去 */
async function sendImage(file) {
  if (!file) return
  if (!file.type || !file.type.startsWith('image/')) {
    ElMessage.warning(`「${file.name || '该文件'}」不是图片，已跳过`)
    return
  }
  uploading.value += 1
  try {
    const url = await uploadFile(file)
    dispatch(url, 'IMAGE')
  } catch (err) {
    ElMessage.error(err?.message || '图片上传失败，请重试')
  } finally {
    uploading.value = Math.max(0, uploading.value - 1)
  }
}

function onPaste(event) {
  const items = Array.from(event.clipboardData?.items || [])
  const images = items
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter(Boolean)
  if (!images.length) return
  event.preventDefault()
  images.forEach(sendImage)
}

/* ------------------------- 入站帧处理 ------------------------- */

function handleInbound(payload) {
  const type = payload?.type
  if (!type) return

  if (type === 'ACK') {
    if (payload.success === false) {
      // 落库失败（如超长）：把最后一条还没被确认的乐观气泡撤掉，别让它留在界面上
      const index = messages.value.findIndex((item) => String(item.id).startsWith('local-'))
      if (index >= 0) messages.value.splice(index, 1)
      ElMessage.error(payload.message || '消息发送失败')
      return
    }
    // 用服务端真实消息替换乐观气泡（替换而不是再 push，否则同一条会显示两遍）
    const index = messages.value.findIndex(
      (item) =>
        String(item.id).startsWith('local-') &&
        item.content === payload.content &&
        (item.msgType || 'TEXT') === (payload.msgType || 'TEXT')
    )
    if (index >= 0) {
      messages.value.splice(index, 1, { ...payload, isMine: true })
    }
    loadLists()
    return
  }

  if (type === 'MESSAGE' || type === 'GROUP_MESSAGE') {
    const isGroup = type === 'GROUP_MESSAGE'
    const channelId = String((isGroup ? payload.groupId : payload.conversationId) || '')
    const sameChannel = activeId.value === channelId && activeType.value === (isGroup ? 'GROUP' : 'PRIVATE')

    if (sameChannel && !messages.value.some((item) => item.id && String(item.id) === String(payload.id))) {
      messages.value.push(payload)
      scrollToBottom()
      // 正在看这个渠道就直接标已读，不用等用户手动再点一次
      markRead()
    }
    loadLists()
    badgeStore.refresh()
  }
}

/* ------------------------- 建群 ------------------------- */

const groupDialog = reactive({
  visible: false,
  name: '',
  notice: '',
  selected: [],
  candidates: [],
  loading: false,
  submitting: false
})

/** 候选成员来自「我关注的人 + 关注我的人」——比全站搜索更贴近真实社交关系 */
async function buildCandidates() {
  groupDialog.loading = true
  try {
    const [followingResult, followersResult] = await Promise.allSettled([
      followApi.listFollowing(userStore.userId),
      followApi.listFollowers(userStore.userId)
    ])
    const map = new Map()
    for (const result of [followingResult, followersResult]) {
      if (result.status !== 'fulfilled') continue
      for (const user of result.value || []) {
        if (user?.userId && user.userId !== userStore.userId) {
          map.set(user.userId, user)
        }
      }
    }
    groupDialog.candidates = [...map.values()]
  } finally {
    groupDialog.loading = false
  }
}

function openGroupDialog() {
  groupDialog.visible = true
  groupDialog.name = ''
  groupDialog.notice = ''
  groupDialog.selected = []
  buildCandidates()
  // 通知父组件把 autoCreateGroup 复位，否则下次打开面板还会自动弹窗
  emit('group-request-consumed')
}

async function submitGroup() {
  if (!groupDialog.name.trim()) {
    ElMessage.warning('请输入群名称')
    return
  }
  // 不再强制「至少选一位书友」：进群需要对方同意，先建群再邀请更符合实际
  groupDialog.submitting = true
  try {
    const group = await chatApi.createGroup({
      name: groupDialog.name.trim(),
      notice: groupDialog.notice.trim() || undefined,
      memberIds: groupDialog.selected
    })
    ElMessage.success(
      groupDialog.selected.length ? '群聊已创建，邀请已发出' : '群聊已创建，可以开始邀请了'
    )
    groupDialog.visible = false
    await loadGroups()
    tab.value = 'group'
    openChannel('GROUP', group?.id)
  } catch {
    // 拦截器已提示
  } finally {
    groupDialog.submitting = false
  }
}

/* ------------------------- 群成员管理 ------------------------- */

const memberDialog = reactive({
  visible: false,
  loading: false,
  members: [],
  inviteSelected: [],
  inviteCandidates: [],
  inviting: false
})

async function openMemberDialog() {
  if (activeType.value !== 'GROUP' || !activeId.value) return
  memberDialog.visible = true
  memberDialog.loading = true
  memberDialog.inviteSelected = []
  try {
    const detail = await chatApi.getGroupDetail(activeId.value)
    memberDialog.members = detail?.members || []
    const existing = new Set(memberDialog.members.map((item) => item.userId))
    await buildCandidates()
    memberDialog.inviteCandidates = groupDialog.candidates.filter((item) => !existing.has(item.userId))
  } catch {
    memberDialog.members = []
  } finally {
    memberDialog.loading = false
  }
  // 群主看板：待审批的加入申请（非群主内部会直接跳过）
  await loadJoinRequests()
}

async function submitInvite() {
  if (!memberDialog.inviteSelected.length) {
    ElMessage.warning('请先选择要邀请的书友')
    return
  }
  memberDialog.inviting = true
  try {
    await chatApi.inviteGroupMembers(activeId.value, memberDialog.inviteSelected)
    // 邀请不等于入群：对方还要在自己那边点「同意」
    ElMessage.success('邀请已发出，等待对方同意')
    memberDialog.inviteSelected = []
    await Promise.all([loadGroups(), openMemberDialog()])
  } catch {
    // 拦截器已提示
  } finally {
    memberDialog.inviting = false
  }
}

/** 退群 / 解散：群主看到的是「解散并清空消息」，成员看到的是「退出」 */
async function leaveOrDissolve() {
  const group = activeGroup.value
  if (!group) return
  const isOwner = Boolean(group.isOwner)
  try {
    await ElMessageBox.confirm(
      isOwner ? '解散后群消息将被清除，且不可恢复，确定解散吗？' : '退出后需要重新被邀请才能进群，确定退出吗？',
      isOwner ? '解散群聊' : '退出群聊',
      { type: 'warning', confirmButtonText: isOwner ? '解散' : '退出', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  if (isOwner) {
    await chatApi.dissolveGroup(group.id)
  } else {
    await chatApi.removeGroupMember(group.id, userStore.userId)
  }
  ElMessage.success(isOwner ? '群聊已解散' : '已退出群聊')
  memberDialog.visible = false
  activeId.value = ''
  if (props.compact) chatOpen.value = false
  await loadGroups()
}

/* ------------------------- 我收到的入群邀请 -------------------------
   别人邀我进群时不会直接把我塞进去，而是先在这里等我点「同意」。
   ------------------------------------------------------------------ */

const invitations = ref([])
const loadingInvite = ref(false)

async function loadInvitations() {
  loadingInvite.value = true
  try {
    invitations.value = (await chatApi.listMyInvitations()) || []
  } catch {
    invitations.value = []
  } finally {
    loadingInvite.value = false
  }
}

/** 同意 / 拒绝收到的入群邀请 */
async function answerInvitation(item, accept) {
  try {
    await chatApi.handleInvitation(item.id, accept)
  } catch {
    // 拦截器已提示
    return
  }
  ElMessage.success(accept ? `已加入「${item.groupName}」` : '已拒绝该邀请')
  invitations.value = invitations.value.filter((one) => String(one.id) !== String(item.id))
  await loadGroups()
  if (accept) {
    tab.value = 'group'
    openChannel('GROUP', item.groupId)
  }
}

/* ------------------------- 发现群聊 / 加入别人的群 ------------------------- */

const discoverDialog = reactive({
  visible: false,
  keyword: '',
  loading: false,
  searched: false,
  groups: [],
  applying: '',
  /** 我已提交且仍在等审批的群 ID，用来把按钮置成「已申请」 */
  pendingIds: []
})

async function openDiscoverDialog() {
  discoverDialog.visible = true
  discoverDialog.keyword = ''
  discoverDialog.groups = []
  discoverDialog.searched = false
  discoverDialog.applying = ''
  await Promise.all([loadPendingJoins(), runSearch()])
  emit('group-request-consumed')
}

async function loadPendingJoins() {
  try {
    discoverDialog.pendingIds = (await chatApi.listMyPendingJoins()) || []
  } catch {
    discoverDialog.pendingIds = []
  }
}

async function runSearch() {
  const keyword = discoverDialog.keyword.trim()
  discoverDialog.loading = true
  discoverDialog.searched = true
  try {
    discoverDialog.groups = keyword ? (await chatApi.searchGroups(keyword)) || [] : []
  } catch {
    discoverDialog.groups = []
  } finally {
    discoverDialog.loading = false
  }
}

function hasApplied(groupId) {
  return discoverDialog.pendingIds.some((id) => String(id) === String(groupId))
}

async function applyJoin(group) {
  discoverDialog.applying = String(group.id)
  try {
    await chatApi.applyJoinGroup(group.id, '')
    ElMessage.success(`已向「${group.name}」提交申请，等待群主通过`)
    await loadPendingJoins()
  } catch {
    // 拦截器已提示
  } finally {
    discoverDialog.applying = ''
  }
}

/* ------------------------- 群主审批加入申请 ------------------------- */

const joinRequests = ref([])
const loadingRequests = ref(false)

async function loadJoinRequests() {
  // 只有群主能看到审批入口，非群主直接清空，省一次必然 403 的请求
  if (!activeGroup.value?.isOwner) {
    joinRequests.value = []
    return
  }
  loadingRequests.value = true
  try {
    joinRequests.value = (await chatApi.listJoinRequests(activeId.value)) || []
  } catch {
    joinRequests.value = []
  } finally {
    loadingRequests.value = false
  }
}

async function answerJoinRequest(item, approve) {
  try {
    if (approve) {
      await chatApi.approveJoinRequest(item.id)
    } else {
      await chatApi.rejectJoinRequest(item.id)
    }
  } catch {
    // 拦截器已提示
    return
  }
  ElMessage.success(approve ? '已通过该申请' : '已拒绝该申请')
  joinRequests.value = joinRequests.value.filter((one) => String(one.id) !== String(item.id))
  // 处理完立即刷新红点：本地把待审批数同步减一，比等下一轮 60s 轮询体感好得多
  badgeStore.groupRequestCount = Math.max(0, Number(badgeStore.groupRequestCount || 0) - 1)
  if (approve) await loadGroups()
}

/* ------------------------- 生命周期 ------------------------- */

watch([activeId, activeType], () => {
  loadMessages()
  checkOnline()
})

watch(
  () => props.initial,
  (value) => {
    if (value?.id) {
      tab.value = value.type === 'GROUP' ? 'group' : 'private'
      openChannel(value.type || 'PRIVATE', value.id)
    }
  }
)

/** 父组件指定初始 Tab（侧边栏点「群聊」进来时用） */
watch(
  () => props.initialTab,
  (value) => {
    if (value === 'group' || value === 'private') {
      tab.value = value
      if (props.compact) chatOpen.value = false
    }
  }
)

/**
 * 建群弹窗的触发时机：
 * 父组件（如 ChatView）往往在 onMounted 里异步准备好参数，那时本组件已经挂载完了，
 * 只靠 onMounted 里读一次 props 会漏掉 —— 这里补一个 watch 兜住。
 */
watch(
  () => props.autoCreateGroup,
  (value) => {
    if (value) openGroupDialog()
  }
)

/** 「发现群聊」同理，父组件把开关置 true 即弹出 */
watch(
  () => props.autoDiscoverGroup,
  (value) => {
    if (value) openDiscoverDialog()
  }
)

onMounted(async () => {
  await Promise.all([loadLists(), loadInvitations()])
  // 订阅入站帧：ACK 与对方推送都从这里进来
  unsubscribe = chatStore.onEvent(handleInbound)

  if (props.initialTab === 'group' || props.initialTab === 'private') {
    tab.value = props.initialTab
  }

  if (props.initial?.id) {
    tab.value = props.initial.type === 'GROUP' ? 'group' : 'private'
    openChannel(props.initial.type || 'PRIVATE', props.initial.id)
  } else if (!props.initialTab && !activeId.value) {
    // 默认打开最新一个会话：比留个空面板更符合直觉。
    // 但父组件明确指定了 Tab 时不要抢戏，否则点「群聊」会莫名其妙打开一个私聊。
    const first = conversations.value[0] || groups.value[0]
    if (first) {
      tab.value = conversations.value[0] ? 'private' : 'group'
      openChannel(conversations.value[0] ? 'PRIVATE' : 'GROUP', first.id)
    }
  }

  if (props.autoCreateGroup) openGroupDialog()
  if (props.autoDiscoverGroup) openDiscoverDialog()

  // 父组件先 mount 再改 initial 的时序问题：这里再兜一次
  await nextTick()
})

onUnmounted(() => {
  unsubscribe?.()
})
</script>

<template>
  <div :class="['chat-panel', compact ? 'is-compact' : 'is-wide']">
    <!-- 会话列表 -->
    <aside v-show="!compact || !chatOpen" class="conv-panel">
      <div class="panel-head">
        <div class="tabs">
          <button type="button" :class="['tab', { active: tab === 'private' }]" @click="switchTab('private')">
            私信
            <span v-if="privateUnreadTotal" class="tab-dot">{{ privateUnreadTotal > 99 ? '99+' : privateUnreadTotal }}</span>
          </button>
          <button type="button" :class="['tab', { active: tab === 'group' }]" @click="switchTab('group')">
            群聊
            <span v-if="groupUnreadTotal" class="tab-dot">{{ groupUnreadTotal > 99 ? '99+' : groupUnreadTotal }}</span>
            <span v-else-if="invitations.length" class="tab-dot">{{ invitations.length }}</span>
          </button>
        </div>
        <div class="head-actions">
          <el-button link size="small" title="发现群聊（加入别人的群）" @click="openDiscoverDialog">
            <el-icon><Search /></el-icon>
          </el-button>
          <el-button link size="small" title="创建群聊" @click="openGroupDialog">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 待我确认的入群邀请：别人邀我进群，必须点「同意」才会真正入群 -->
      <div v-if="tab === 'group' && invitations.length" class="invite-box">
        <p class="invite-title">入群邀请（{{ invitations.length }}）</p>
        <div v-for="item in invitations" :key="item.id" class="invite-item">
          <BnAvatar :src="item.groupAvatar" :name="item.groupName" :size="32" :linkable="false" />
          <div class="invite-body">
            <p class="invite-text bn-ellipsis-1">
              <b>{{ item.inviterName || '书友' }}</b> 邀请你加入「{{ item.groupName }}」
            </p>
            <p class="invite-sub">{{ item.memberCount || 0 }} 位成员</p>
          </div>
          <div class="invite-actions">
            <el-button size="small" type="primary" @click="answerInvitation(item, true)">同意</el-button>
            <el-button size="small" text @click="answerInvitation(item, false)">拒绝</el-button>
          </div>
        </div>
      </div>

      <el-skeleton v-if="listLoading && !list.length" :rows="5" animated />

      <div v-else-if="!list.length" class="panel-empty">
        <el-icon :size="28" color="#c8bdb1"><ChatDotRound /></el-icon>
        <p class="bn-mt-12">{{ tab === 'group' ? '还没有群聊' : '还没有会话' }}</p>
        <p class="empty-tip">{{ tab === 'group' ? '点右上角 + 拉几位书友聊聊' : '在书友主页点「私信」开始聊天' }}</p>
      </div>

      <div v-else class="conv-list">
        <div
          v-for="item in list"
          :key="`${tab}-${item.id}`"
          :class="[
            'conv-item',
            { active: String(item.id) === activeId && (tab === 'group' ? activeType === 'GROUP' : activeType === 'PRIVATE') }
          ]"
          @click="openChannel(tab === 'group' ? 'GROUP' : 'PRIVATE', item.id)"
        >
          <BnAvatar
            :src="tab === 'group' ? item.avatar : item.peerAvatar"
            :name="tab === 'group' ? item.name : item.peerName"
            :size="38"
            :linkable="false"
          />
          <div class="conv-body">
            <div class="conv-top">
              <span class="conv-name bn-ellipsis-1">
                {{ (tab === 'group' ? item.name : item.peerName) || '书友' }}
              </span>
              <span class="conv-time">{{ fromNow(item.lastMsgAt) }}</span>
            </div>
            <p class="conv-last bn-ellipsis-1">
              {{ item.lastMessage || (tab === 'group' ? '群聊已创建' : '暂无消息') }}
            </p>
          </div>
          <el-badge v-if="item.unreadCount" :value="item.unreadCount" :max="99" class="conv-badge" />
        </div>
      </div>
    </aside>

    <!-- 消息面板 -->
    <section v-show="!compact || chatOpen" class="msg-panel">
      <template v-if="activeId">
        <header class="msg-head">
          <button v-if="compact" type="button" class="back-btn" title="返回会话列表" @click="backToList">
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <BnAvatar
            :src="channelAvatar"
            :name="channelName"
            :size="34"
            :user-id="activeType === 'PRIVATE' ? peerId : ''"
          />
          <div class="msg-head-body">
            <button
              type="button"
              class="msg-channel"
              :disabled="activeType === 'GROUP'"
              @click="goProfile"
            >
              {{ channelName }}
            </button>
            <span :class="['channel-sub', { on: activeType === 'PRIVATE' && peerOnline }]">
              {{ channelSubtitle }}
            </span>
          </div>
          <el-tag v-if="activeType === 'GROUP'" size="small" effect="plain" class="group-tag">
            <el-icon><UserFilled /></el-icon>群聊
          </el-tag>
          <el-button
            v-if="activeType === 'GROUP'"
            link
            size="small"
            class="member-btn"
            @click="openMemberDialog"
          >
            群信息
          </el-button>
        </header>

        <div ref="scrollerRef" class="msg-scroller" @scroll="onScroll">
          <el-skeleton v-if="loadingMsg && !messages.length" :rows="4" animated />

          <template v-else>
            <div v-if="!messages.length" class="msg-empty">
              <p class="bn-text-muted">还没有消息，打个招呼吧</p>
            </div>

            <div
              v-for="(msg, index) in messages"
              :key="msg.id || index"
              :class="['msg-row', { mine: msg.isMine ?? String(msg.senderId) === String(userStore.userId) }]"
            >
              <BnAvatar
                :src="
                  msg.senderAvatar ||
                  (String(msg.senderId) === String(userStore.userId) ? userStore.avatar : channelAvatar)
                "
                :name="
                  msg.senderName ||
                  (String(msg.senderId) === String(userStore.userId) ? userStore.username : channelName)
                "
                :size="32"
                :linkable="false"
              />
              <div class="msg-bubble-wrap">
                <span
                  v-if="activeType === 'GROUP' && String(msg.senderId) !== String(userStore.userId)"
                  class="msg-sender"
                >
                  {{ msg.senderName || '书友' }}
                </span>

                <!-- 图片消息：content 就是 OSS URL -->
                <a
                  v-if="msg.msgType === 'IMAGE'"
                  :href="msg.content"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="msg-image"
                >
                  <img :src="msg.content" alt="图片消息" loading="lazy" />
                </a>
                <div v-else class="msg-bubble">{{ msg.content }}</div>

                <span class="msg-time">
                  <el-icon v-if="String(msg.id).startsWith('local-')" class="sending"><Loading /></el-icon>
                  {{ fromNow(msg.createTime) }}
                </span>
              </div>
            </div>
          </template>
        </div>

        <footer class="msg-composer">
          <div class="composer-tools">
            <button type="button" class="tool-btn" title="发送图片" :disabled="uploading > 0" @click="triggerImagePick">
              <el-icon><Picture /></el-icon>
            </button>
            <button type="button" class="tool-btn" title="表情" @click="showEmoji = !showEmoji">
              <el-icon><Sunny /></el-icon>
            </button>
            <span v-if="uploading > 0" class="uploading-tip">图片上传中…</span>
          </div>

          <div v-if="showEmoji" class="emoji-board">
            <button v-for="emoji in EMOJIS" :key="emoji" type="button" class="emoji" @click="pickEmoji(emoji)">
              {{ emoji }}
            </button>
          </div>

          <el-input
            ref="composerRef"
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入消息，Enter 发送，Shift + Enter 换行"
            @keydown.enter.exact.prevent="send"
            @paste="onPaste"
          />
          <el-button type="primary" :loading="uploading > 0" @click="send">发送</el-button>
        </footer>
      </template>

      <div v-else class="panel-empty no-channel">
        <el-icon :size="34" color="#c8bdb1"><ChatLineRound /></el-icon>
        <p class="bn-mt-12">选择左侧会话开始聊天</p>
      </div>
    </section>

    <!-- 隐藏的文件选择器 -->
    <input ref="fileInputRef" type="file" accept="image/*" multiple class="hidden-file" @change="onFilePicked" />

    <!-- 建群弹窗 -->
    <el-dialog v-model="groupDialog.visible" title="创建群聊" width="460px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="群名称">
          <el-input v-model="groupDialog.name" maxlength="30" show-word-limit placeholder="给群聊起个名字" />
        </el-form-item>
        <el-form-item label="群公告（可选）">
          <el-input v-model="groupDialog.notice" maxlength="200" placeholder="例如：每周共读一本书" />
        </el-form-item>
        <el-form-item label="邀请书友（来自你关注的人和粉丝）">
          <el-skeleton v-if="groupDialog.loading" :rows="3" animated />
          <div v-else-if="!groupDialog.candidates.length" class="dialog-empty">
            还没有可邀请的书友，先去关注几位书友吧
          </div>
          <el-checkbox-group v-else v-model="groupDialog.selected" class="candidate-grid">
            <el-checkbox v-for="user in groupDialog.candidates" :key="user.userId" :value="user.userId">
              {{ user.username || '书友' }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="groupDialog.submitting" @click="submitGroup">创建</el-button>
      </template>
    </el-dialog>

    <!-- 群信息弹窗 -->
    <el-dialog v-model="memberDialog.visible" title="群信息" width="460px" append-to-body>
      <el-skeleton v-if="memberDialog.loading" :rows="4" animated />
      <template v-else>
        <p class="member-section-title">群成员（{{ memberDialog.members.length }}）</p>
        <div class="member-list">
          <div v-for="member in memberDialog.members" :key="member.userId" class="member-item">
            <BnAvatar :src="member.avatar" :name="member.username" :size="30" :user-id="member.userId" />
            <span class="member-name bn-ellipsis-1">{{ member.username || '书友' }}</span>
            <el-tag v-if="member.role === 'OWNER'" size="small" effect="plain">群主</el-tag>
          </div>
        </div>

        <template v-if="memberDialog.inviteCandidates.length">
          <p class="member-section-title bn-mt-16">邀请更多书友</p>
          <el-checkbox-group v-model="memberDialog.inviteSelected" class="candidate-grid">
            <el-checkbox v-for="user in memberDialog.inviteCandidates" :key="user.userId" :value="user.userId">
              {{ user.username || '书友' }}
            </el-checkbox>
          </el-checkbox-group>
          <el-button size="small" class="bn-mt-12" :loading="memberDialog.inviting" @click="submitInvite">
            邀请入群
          </el-button>
        </template>

        <template v-if="activeGroup?.isOwner">
          <p class="member-section-title bn-mt-16">
            待审批的加入申请（{{ joinRequests.length }}）
          </p>
          <div v-if="loadingRequests" class="dialog-empty">加载中…</div>
          <div v-else-if="!joinRequests.length" class="dialog-empty">暂时没有人申请加入</div>
          <div v-else class="join-list">
            <div v-for="item in joinRequests" :key="item.id" class="join-item">
              <BnAvatar
                :src="item.inviteeAvatar"
                :name="item.inviteeName"
                :size="28"
                :user-id="item.inviteeId"
              />
              <span class="join-name bn-ellipsis-1">{{ item.inviteeName || '书友' }}</span>
              <el-button size="small" type="primary" @click="answerJoinRequest(item, true)">通过</el-button>
              <el-button size="small" text @click="answerJoinRequest(item, false)">拒绝</el-button>
            </div>
          </div>
        </template>
      </template>

      <template #footer>
        <el-button type="danger" plain @click="leaveOrDissolve">
          {{ activeGroup?.isOwner ? '解散群聊' : '退出群聊' }}
        </el-button>
        <el-button @click="memberDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 发现群聊：搜索并申请加入别人的群（要等群主审批） -->
    <el-dialog v-model="discoverDialog.visible" title="发现群聊" width="480px" append-to-body>
      <div class="discover-search">
        <el-input
          v-model="discoverDialog.keyword"
          placeholder="输入群名称搜索"
          clearable
          @keyup.enter="runSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" :loading="discoverDialog.loading" @click="runSearch">搜索</el-button>
      </div>

      <el-skeleton v-if="discoverDialog.loading" :rows="3" animated class="bn-mt-16" />

      <template v-else>
        <div v-if="!discoverDialog.searched" class="dialog-empty bn-mt-16">
          输入群名称开始搜索，找到感兴趣的群后可以申请加入
        </div>
        <div v-else-if="!discoverDialog.groups.length" class="dialog-empty bn-mt-16">
          没有找到可加入的群，换个关键词试试
        </div>
        <div v-else class="discover-list">
          <div v-for="item in discoverDialog.groups" :key="item.id" class="discover-item">
            <BnAvatar :src="item.avatar" :name="item.name" :size="34" :linkable="false" />
            <div class="discover-body">
              <p class="discover-name bn-ellipsis-1">{{ item.name }}</p>
              <p class="discover-sub">
                {{ item.memberCount || 0 }} 位成员
                <template v-if="item.ownerName"> · 群主 {{ item.ownerName }}</template>
              </p>
            </div>
            <el-button
              size="small"
              type="primary"
              :disabled="hasApplied(item.id)"
              :loading="discoverDialog.applying === String(item.id)"
              @click="applyJoin(item)"
            >
              {{ hasApplied(item.id) ? '已申请' : '申请加入' }}
            </el-button>
          </div>
        </div>
      </template>

      <template #footer>
        <el-button @click="discoverDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-panel {
  display: flex;
  min-height: 0;
  height: 100%;
}

.chat-panel.is-wide {
  gap: 16px;
}

.chat-panel.is-compact {
  flex-direction: column;
}

/* ---------- 会话列表 ---------- */
.conv-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.is-wide .conv-panel {
  width: 268px;
  flex-shrink: 0;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius);
  background: #fff;
}

.is-compact .conv-panel {
  flex: 1;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--bn-border);
}

.tabs {
  display: flex;
  gap: 4px;
}

.tab {
  position: relative;
  border: none;
  background: transparent;
  padding: 4px 10px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 13px;
  color: var(--bn-text-sub);
  cursor: pointer;
  transition: all 0.15s ease;
}

.tab:hover {
  color: var(--bn-primary);
}

.tab.active {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
  font-weight: 600;
}

.tab-dot {
  display: inline-block;
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 999px;
  background: #e4573d;
  color: #fff;
  font-size: 10px;
  line-height: 15px;
  vertical-align: 1px;
}

.panel-empty {
  padding: 40px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--bn-text-muted);
}

.empty-tip {
  font-size: 12px;
  margin-top: 4px;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.conv-item:hover {
  background: #f9f6f3;
}

.conv-item.active {
  background: var(--bn-primary-soft);
}

.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conv-name {
  font-size: 13.5px;
  font-weight: 600;
  flex: 1;
  min-width: 0;
}

.conv-time {
  font-size: 11px;
  color: var(--bn-text-muted);
  flex-shrink: 0;
}

.conv-last {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  margin-top: 3px;
}

.conv-badge {
  position: absolute;
  right: 8px;
  bottom: 6px;
}

/* ---------- 消息面板 ---------- */
.msg-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.is-wide .msg-panel {
  flex: 1;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius);
  background: #fff;
}

.is-compact .msg-panel {
  flex: 1;
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--bn-border);
  background: #fdfcfa;
}

.back-btn {
  border: none;
  background: transparent;
  padding: 2px;
  color: var(--bn-text-sub);
  cursor: pointer;
}

.msg-head-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.msg-channel {
  border: none;
  background: transparent;
  padding: 0;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--bn-text);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-channel:disabled {
  cursor: default;
}

.msg-channel:not(:disabled):hover {
  color: var(--bn-primary);
}

.channel-sub {
  font-size: 11.5px;
  color: var(--bn-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.channel-sub.on {
  color: #4a9c6d;
}

.channel-sub.on::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4a9c6d;
}

.group-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.member-btn {
  flex-shrink: 0;
}

.msg-scroller {
  flex: 1;
  overflow-y: auto;
  padding: 16px 14px;
  min-height: 180px;
}

.msg-empty {
  text-align: center;
  padding: 50px 0;
  font-size: 13px;
}

.msg-row {
  display: flex;
  gap: 9px;
  margin-bottom: 15px;
  align-items: flex-start;
}

.msg-row.mine {
  flex-direction: row-reverse;
}

.msg-bubble-wrap {
  max-width: 70%;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.msg-row.mine .msg-bubble-wrap {
  align-items: flex-end;
}

.msg-sender {
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

.msg-bubble {
  padding: 8px 12px;
  border-radius: 10px;
  background: #faf8f5;
  border: 1px solid var(--bn-border);
  font-size: 13.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-row.mine .msg-bubble {
  background: var(--bn-primary);
  color: #fff;
  border-color: var(--bn-primary);
}

.msg-image {
  display: block;
  max-width: 200px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--bn-border);
}

.msg-image img {
  display: block;
  width: 100%;
  max-height: 220px;
  object-fit: cover;
}

.msg-time {
  font-size: 11px;
  color: var(--bn-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.sending {
  animation: bn-spin 1s linear infinite;
}

@keyframes bn-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------- 输入区 ---------- */
.msg-composer {
  border-top: 1px solid var(--bn-border);
  background: #fdfcfa;
  padding: 8px 12px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--bn-text-sub);
  cursor: pointer;
  transition: all 0.15s ease;
}

.tool-btn:hover:not(:disabled) {
  background: #fff;
  border-color: var(--bn-border);
  color: var(--bn-primary);
}

.tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.uploading-tip {
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

.emoji-board {
  position: absolute;
  bottom: 108px;
  left: 12px;
  z-index: 5;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  padding: 8px;
  background: #fff;
  border: 1px solid var(--bn-border);
  border-radius: 10px;
  box-shadow: var(--bn-shadow);
}

.emoji {
  border: none;
  background: transparent;
  font-size: 17px;
  line-height: 1;
  padding: 4px;
  border-radius: 6px;
  cursor: pointer;
}

.emoji:hover {
  background: var(--bn-primary-soft);
}

.msg-composer :deep(.el-textarea) {
  flex: 1;
}

.msg-composer :deep(.el-textarea__inner) {
  font-size: 13.5px;
}

.no-channel {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.hidden-file {
  display: none;
}

/* ---------- 弹窗内 ---------- */
.candidate-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  max-height: 180px;
  overflow-y: auto;
  width: 100%;
}

.dialog-empty {
  font-size: 12.5px;
  color: var(--bn-text-muted);
}

.member-section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 190px;
  overflow-y: auto;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 9px;
}

.member-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

/* ---------- 头部操作区 ---------- */
.head-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

/* ---------- 待确认的入群邀请 ---------- */
.invite-box {
  padding: 10px 12px;
  border-bottom: 1px solid var(--bn-border);
  background: #fff8f0;
}

.invite-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--bn-primary);
  margin-bottom: 8px;
}

.invite-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 0;
}

.invite-item + .invite-item {
  border-top: 1px dashed #f0e2d2;
}

.invite-body {
  flex: 1;
  min-width: 0;
}

.invite-text {
  font-size: 12.5px;
  line-height: 1.5;
}

.invite-sub {
  font-size: 11px;
  color: var(--bn-text-muted);
  margin-top: 2px;
}

.invite-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

/* ---------- 发现群聊 ---------- */
.discover-search {
  display: flex;
  align-items: center;
  gap: 8px;
}

.discover-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 320px;
  overflow-y: auto;
  margin-top: 14px;
}

.discover-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--bn-border);
  border-radius: 9px;
}

.discover-body {
  flex: 1;
  min-width: 0;
}

.discover-name {
  font-size: 13.5px;
  font-weight: 600;
}

.discover-sub {
  font-size: 11.5px;
  color: var(--bn-text-muted);
  margin-top: 2px;
}

/* ---------- 群主审批加入申请 ---------- */
.join-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.join-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.join-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}
</style>
