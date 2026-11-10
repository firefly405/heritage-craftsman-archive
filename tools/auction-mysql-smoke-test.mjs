import { randomBytes } from 'node:crypto'

const baseUrl = (process.env.AUCTION_BASE_URL || 'http://127.0.0.1:8081/api').replace(/\/+$/, '')
const adminUsername = process.env.AUCTION_ADMIN_USERNAME?.trim()
const adminPassword = process.env.AUCTION_ADMIN_PASSWORD

function stopForSafety(message) {
  console.error(`\n[拍卖冒烟测试安全检查失败] ${message}\n`)
  process.exit(1)
}

let targetUrl
try {
  targetUrl = new URL(baseUrl)
} catch {
  stopForSafety('AUCTION_BASE_URL 不是有效 URL。')
}

if (!['http:', 'https:'].includes(targetUrl.protocol)) {
  stopForSafety('AUCTION_BASE_URL 只能使用 http 或 https。')
}
if (!['127.0.0.1', 'localhost', '[::1]'].includes(targetUrl.hostname)) {
  stopForSafety('仅允许连接 127.0.0.1、localhost 或 ::1 上的一次性本地验收环境，禁止对远程或正式环境运行。')
}
if (!adminUsername || !adminPassword?.trim()) {
  stopForSafety('必须显式设置 AUCTION_ADMIN_USERNAME 和 AUCTION_ADMIN_PASSWORD；脚本不会使用默认管理员或弱口令。')
}
if (process.env.AUCTION_SMOKE_ALLOW_LOCAL_DATA !== 'YES') {
  stopForSafety('该脚本会留下测试账号和业务数据。确认目标是可丢弃的本地验收库后，请显式设置 AUCTION_SMOKE_ALLOW_LOCAL_DATA=YES。')
}

console.warn(`
================================================================
[安全提示] 即将对 ${baseUrl} 执行拍卖端到端验收。
仅允许使用一次性、可丢弃的本地验收数据库，严禁连接正式数据库。
本脚本会创建测试账号、档案、作品、拍卖和操作日志，执行后不会自动删除。
================================================================
`)

function randomTestPassword() {
  return `Smoke-${randomBytes(24).toString('base64url')}-Aa1!`
}

function localDateTime(offsetMs) {
  const date = new Date(Date.now() + offsetMs)
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function request(path, { method = 'GET', token, body, allowFailure = false } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' })
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const payload = await response.json().catch(() => ({}))
  const ok = response.ok && payload.success !== false
  if (!ok && !allowFailure) throw new Error(`${method} ${path}: ${payload.message || response.status}`)
  return { ok, status: response.status, payload }
}

async function register(username, realName, password) {
  return (await request('/auth/register', {
    method: 'POST',
    body: { username, password, realName, phone: '13800000000', role: 'ARTISAN' }
  })).payload.data
}

async function login(username, password) {
  return (await request('/auth/login', { method: 'POST', body: { username, password } })).payload.data
}

const adminSession = await login(adminUsername, adminPassword)
if (adminSession.user?.role !== 'ADMIN') {
  stopForSafety('AUCTION_ADMIN_USERNAME 对应账号不是管理员，尚未创建任何测试数据。')
}

const suffix = `${Date.now()}`
const sellerPassword = randomTestPassword()
const bidderAPassword = randomTestPassword()
const bidderBPassword = randomTestPassword()
const seller = await register(`auction_seller_${suffix}`, '实库测试传承人', sellerPassword)
const bidderA = await register(`auction_bid_a_${suffix}`, '实库竞拍甲', bidderAPassword)
const bidderB = await register(`auction_bid_b_${suffix}`, '实库竞拍乙', bidderBPassword)
const sellerSession = await login(seller.username, sellerPassword)
const bidderASession = await login(bidderA.username, bidderAPassword)
const bidderBSession = await login(bidderB.username, bidderBPassword)

const artisan = (await request('/artisans', {
  method: 'POST',
  token: adminSession.token,
  body: {
    userId: seller.id,
    name: `实库剪纸传承人${suffix.slice(-5)}`,
    gender: '女',
    birthYear: 1988,
    phone: '13800000000',
    region: '黑龙江省哈尔滨市',
    categoryId: 1,
    experienceYears: 20,
    skillDescription: '传统剪纸技艺与数字化创新',
    honors: '非遗创新展示作品',
    inheritanceStory: '用于自动化验收的演示档案'
  }
})).payload.data
await request(`/artisans/${artisan.id}/submit`, { method: 'POST', token: adminSession.token })
await request(`/artisans/${artisan.id}/review`, {
  method: 'POST', token: adminSession.token, body: { approved: true, comment: '实库测试审核通过' }
})

const work = (await request('/works', {
  method: 'POST',
  token: adminSession.token,
  body: {
    artisanId: artisan.id,
    title: `冰城记忆剪纸${suffix.slice(-5)}`,
    description: '传统剪纸工艺与冰城文化相结合的非遗作品',
    price: 100,
    status: 'DISPLAY'
  }
})).payload.data

const auction = (await request('/auctions', {
  method: 'POST',
  token: adminSession.token,
  body: {
    workId: work.id,
    title: `冰城记忆非遗拍卖${suffix.slice(-5)}`,
    description: 'MySQL 实库端到端验收拍卖',
    startPrice: 100,
    minIncrement: 10,
    startTime: localDateTime(-60_000),
    endTime: localDateTime(3_600_000)
  }
})).payload.data
await request(`/auctions/${auction.id}/publish`, { method: 'POST', token: adminSession.token })

const adminBid = await request(`/auctions/${auction.id}/bid`, {
  method: 'POST', token: adminSession.token, body: { amount: 100 }, allowFailure: true
})
if (adminBid.ok) throw new Error('管理员不应同时拥有竞价和提前结束权限')

const [bidResultA, bidResultB] = await Promise.all([
  request(`/auctions/${auction.id}/bid`, { method: 'POST', token: bidderASession.token, body: { amount: 100 }, allowFailure: true }),
  request(`/auctions/${auction.id}/bid`, { method: 'POST', token: bidderBSession.token, body: { amount: 100 }, allowFailure: true })
])
if ([bidResultA, bidResultB].filter(result => result.ok).length !== 1) {
  throw new Error('MySQL 并发竞价校验失败：相同价格应当且只能有一个请求成功')
}

const selfBid = await request(`/auctions/${auction.id}/bid`, {
  method: 'POST', token: sellerSession.token, body: { amount: 120 }, allowFailure: true
})
if (selfBid.ok) throw new Error('卖家自我竞价校验失败')

const adminAuction = (await request(`/auctions/${auction.id}`, { token: adminSession.token })).payload.data
const bidHistory = (await request(`/auctions/${auction.id}/bids`, { token: adminSession.token })).payload.data
if (adminAuction.bidCount !== 1 || bidHistory.length !== 1) throw new Error('竞价次数或出价记录不一致')

await request(`/auctions/${auction.id}/close`, { method: 'POST', token: adminSession.token })
const closedAuction = (await request(`/auctions/${auction.id}`, { token: adminSession.token })).payload.data
const works = (await request('/works', { token: adminSession.token })).payload.data
if (closedAuction.status !== 'ENDED') throw new Error('管理员结束拍卖后状态不正确')
if (works.find(item => item.id === work.id)?.status !== 'SOLD') throw new Error('成交后作品未标记为 SOLD')

const collisionWork = (await request('/works', {
  method: 'POST', token: adminSession.token,
  body: { artisanId: artisan.id, title: `并发建拍作品${suffix.slice(-5)}`, description: 'MySQL 行锁校验', price: 60, status: 'DISPLAY' }
})).payload.data
const collisionPayload = {
  workId: collisionWork.id, title: `并发建拍校验${suffix.slice(-5)}`, description: '同一作品只能存在一个活动拍卖',
  startPrice: 60, minIncrement: 5, startTime: localDateTime(3_600_000), endTime: localDateTime(7_200_000)
}
const createResults = await Promise.all([
  request('/auctions', { method: 'POST', token: adminSession.token, body: collisionPayload, allowFailure: true }),
  request('/auctions', { method: 'POST', token: adminSession.token, body: collisionPayload, allowFailure: true })
])
if (createResults.filter(result => result.ok).length !== 1) {
  throw new Error('MySQL 并发创建校验失败：同一作品应当且只能生成一个活动拍卖')
}
const collisionAuction = createResults.find(result => result.ok).payload.data
await request(`/auctions/${collisionAuction.id}/cancel`, { method: 'POST', token: adminSession.token })

const privateWork = (await request('/works', {
  method: 'POST', token: adminSession.token,
  body: { artisanId: artisan.id, title: `隐私校验作品${suffix.slice(-5)}`, description: '草稿隐私校验', price: 80, status: 'DISPLAY' }
})).payload.data
const privateAuction = (await request('/auctions', {
  method: 'POST', token: adminSession.token,
  body: {
    workId: privateWork.id, title: `未发布拍卖${suffix.slice(-5)}`, description: '不应被普通用户查看',
    startPrice: 80, minIncrement: 5, startTime: localDateTime(3_600_000), endTime: localDateTime(7_200_000)
  }
})).payload.data
const privateView = await request(`/auctions/${privateAuction.id}`, { token: bidderASession.token, allowFailure: true })
if (privateView.ok) throw new Error('普通用户不应查看他人的拍卖草稿')

await request(`/users/${bidderA.id}/role`, { method: 'PUT', token: adminSession.token, body: { role: 'ADMIN' } })
await request('/users', { token: bidderASession.token })
await request(`/users/${bidderA.id}/role`, { method: 'PUT', token: adminSession.token, body: { role: 'ARTISAN' } })
const staleAdmin = await request('/users', { token: bidderASession.token, allowFailure: true })
if (staleAdmin.ok) throw new Error('角色撤销后旧 Token 仍保留管理员权限')

console.log(JSON.stringify({
  success: true,
  auctionId: auction.id,
  winningBidder: closedAuction.winnerName,
  finalPrice: closedAuction.currentPrice,
  bidCount: closedAuction.bidCount,
  checks: ['MySQL 表初始化', '并发竞价', '同作品并发创建', '禁止管理员/卖家竞价', '成交结算', '草稿隐私', 'Token 权限实时刷新']
}, null, 2))
