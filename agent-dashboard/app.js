/* ============================================
   天天扑克锦标赛 Agent Dashboard - App Logic
   ============================================ */

(function () {
  'use strict';

  // ---- Config ----
  const API_BASE = '/agents/api/v1';
  const PAGE_SIZE = 20;

  // ---- State ----
  let currentRoute = 'dashboard';
  let ordersPage = 1;
  let ordersTotal = 0;

  // ---- Init ----
  function init() {
    const token = localStorage.getItem('pn_agent_token');
    if (!token) {
      window.location.href = 'login.html';
      return;
    }

    // Show agent info
    const info = JSON.parse(localStorage.getItem('pn_agent_info') || '{}');
    document.getElementById('agentInfo').textContent = info.name
      ? `${info.name}（${info.code || '--'}）`
      : '代理';

    // Clock
    updateClock();
    setInterval(updateClock, 60000);

    // Nav events
    document.querySelectorAll('.nav-item').forEach(item => {
      item.addEventListener('click', () => {
        const route = item.dataset.route;
        navigateTo(route);
      });
    });

    // Mobile toggle
    document.getElementById('mobileToggle').addEventListener('click', () => {
      document.getElementById('sidebar').classList.toggle('open');
    });

    // Logout
    document.getElementById('logoutBtn').addEventListener('click', logout);

    // Initial route
    const hash = window.location.hash.slice(1);
    navigateTo(hash || 'dashboard');

    window.addEventListener('hashchange', () => {
      const h = window.location.hash.slice(1);
      if (h) navigateTo(h);
    });
  }

  function updateClock() {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    const hh = String(now.getHours()).padStart(2, '0');
    const mi = String(now.getMinutes()).padStart(2, '0');
    document.getElementById('currentTime').textContent = `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
  }

  // ---- Router ----
  function navigateTo(route) {
    currentRoute = route;
    window.location.hash = route;

    // Update nav
    document.querySelectorAll('.nav-item').forEach(item => {
      item.classList.toggle('active', item.dataset.route === route);
    });

    // Update title
    const titles = {
      dashboard: '看板',
      venues: '我的门店',
      subagents: '我的代理',
      orders: '订单',
      withdrawals: '提现'
    };
    document.getElementById('pageTitle').textContent = titles[route] || '看板';

    // Close sidebar on mobile
    document.getElementById('sidebar').classList.remove('open');

    // Render
    const content = document.getElementById('contentArea');
    content.innerHTML = '';

    switch (route) {
      case 'dashboard': renderDashboard(content); break;
      case 'venues': renderVenues(content); break;
      case 'subagents': renderSubagents(content); break;
      case 'orders': renderOrders(content); break;
      case 'withdrawals': renderWithdrawals(content); break;
      default: renderDashboard(content);
    }
  }

  // ---- API ----
  async function api(path, options) {
    const token = localStorage.getItem('pn_agent_token');
    const opts = options || {};
    opts.headers = opts.headers || {};
    opts.headers['Authorization'] = 'Bearer ' + token;
    if (opts.body && !opts.headers['Content-Type']) {
      opts.headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(API_BASE + path, opts);
    if (res.status === 401) {
      localStorage.removeItem('pn_agent_token');
      window.location.href = 'login.html';
      return null;
    }
    return res.json();
  }

  // ---- Toast ----
  function showToast(msg, type) {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = 'toast ' + (type || '');
    toast.textContent = msg;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
  }

  // ---- Logout ----
  function logout() {
    localStorage.removeItem('pn_agent_token');
    localStorage.removeItem('pn_agent_info');
    window.location.href = 'login.html';
  }

  // ---- Copy helper ----
  function copyText(text, msg) {
    const done = () => showToast(msg || '已复制', 'success');
    const fail = () => showToast('复制失败，请手动复制', 'error');
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, fail);
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      try {
        document.execCommand('copy');
        done();
      } catch {
        fail();
      }
      ta.remove();
    }
  }

  // ============================================
  // Dashboard
  // ============================================
  async function renderDashboard(container) {
    container.innerHTML = `
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">可提余额</div>
          <div class="stat-value gold" id="stAvailable">--</div>
          <div class="stat-sub">可申请提现</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">累计佣金</div>
          <div class="stat-value" id="stTotalIncome">--</div>
          <div class="stat-sub">已支付订单抽成合计</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">已提现 / 提现中</div>
          <div class="stat-value warning" id="stWithdrawn">--</div>
          <div class="stat-sub">含处理中申请</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">相关订单数</div>
          <div class="stat-value" id="stOrders">--</div>
          <div class="stat-sub">已支付</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">我的门店</div>
          <div class="stat-value success" id="stVenues">--</div>
          <div class="stat-sub">家</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">下级代理</div>
          <div class="stat-value" id="stSubAgents">--</div>
          <div class="stat-sub">个</div>
        </div>
      </div>
      <div class="card">
        <div class="card-title"><span>我的邀请码</span></div>
        <div class="invite-code-row">
          <span class="invite-code" id="inviteCode">--</span>
          <button class="btn btn-gold btn-sm" id="copyCodeBtn">复制邀请码</button>
        </div>
        <div class="invite-tips">
          <p>推广方式一：分享下面的注册链接发展下级代理，新代理注册后归到你名下：</p>
          <div class="invite-link-row">
            <code id="inviteLink">--</code>
            <button class="btn btn-outline btn-sm" id="copyLinkBtn">复制链接</button>
          </div>
          <p>推广方式二：让门店在商户后台输入你的邀请码完成绑定，绑定后该店订单按比例给你抽成。</p>
        </div>
      </div>
    `;

    document.getElementById('copyCodeBtn').addEventListener('click', () => {
      const code = document.getElementById('inviteCode').textContent;
      if (code && code !== '--') copyText(code, '邀请码已复制');
    });
    document.getElementById('copyLinkBtn').addEventListener('click', () => {
      const link = document.getElementById('inviteLink').textContent;
      if (link && link !== '--') copyText(link, '注册链接已复制');
    });

    try {
      const data = await api('/agent/summary');
      if (!data) return;
      if (data.error) {
        showToast(data.error, 'error');
        return;
      }
      document.getElementById('stAvailable').textContent = fmtYuan(data.available);
      document.getElementById('stTotalIncome').textContent = fmtYuan(data.totalIncome);
      document.getElementById('stWithdrawn').textContent = fmtYuan(data.withdrawn);
      document.getElementById('stOrders').textContent = data.totalOrders ?? 0;
      document.getElementById('stVenues').textContent = data.venueCount ?? 0;
      document.getElementById('stSubAgents').textContent = data.subAgentCount ?? 0;

      const code = data.agent && data.agent.code;
      if (code) {
        document.getElementById('inviteCode').textContent = code;
        document.getElementById('inviteLink').textContent =
          `${window.location.origin}/agents/register.html?code=${code}`;
      }
    } catch (err) {
      showToast('数据加载失败', 'error');
    }
  }

  // ============================================
  // Venues
  // ============================================
  async function renderVenues(container) {
    container.innerHTML = `
      <div class="card">
        <div class="card-title">
          <span>我的门店</span>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>门店名称</th>
                <th>联系人</th>
                <th>联系电话</th>
                <th>已支付订单数</th>
                <th>订单总额</th>
                <th>我的佣金</th>
              </tr>
            </thead>
            <tbody id="venuesTbody">
              <tr><td colspan="6"><div class="loading">加载中</div></td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;

    try {
      const data = await api('/agent/venues');
      if (!data) return;
      const list = data.list || [];
      const tbody = document.getElementById('venuesTbody');

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><p>暂无门店，让门店在商户后台输入你的邀请码完成绑定</p></div></td></tr>';
        return;
      }

      tbody.innerHTML = list.map(v => `<tr>
        <td>${v.name}</td>
        <td>${v.contact}</td>
        <td>${v.phone}</td>
        <td>${v.paidOrders}</td>
        <td>${fmtYuan(v.totalAmount)}</td>
        <td>${fmtYuan(v.myIncome)}</td>
      </tr>`).join('');
    } catch (err) {
      showToast('门店列表加载失败', 'error');
    }
  }

  // ============================================
  // Subagents
  // ============================================
  async function renderSubagents(container) {
    container.innerHTML = `
      <div class="card">
        <div class="card-title">
          <span>我的代理</span>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>姓名</th>
                <th>邀请码</th>
                <th>手机号</th>
                <th>抽成比例</th>
                <th>门店数</th>
                <th>已支付订单数</th>
                <th>贡献给我的佣金</th>
                <th>加入时间</th>
              </tr>
            </thead>
            <tbody id="subagentsTbody">
              <tr><td colspan="8"><div class="loading">加载中</div></td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;

    try {
      const data = await api('/agent/subagents');
      if (!data) return;
      const list = data.list || [];
      const tbody = document.getElementById('subagentsTbody');

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="8"><div class="empty-state"><p>暂无下级代理，去「看板」复制你的注册链接发展代理</p></div></td></tr>';
        return;
      }

      tbody.innerHTML = list.map(a => `<tr>
        <td>${a.name}</td>
        <td>${a.code}</td>
        <td>${a.phone}</td>
        <td>${a.rate}%</td>
        <td>${a.venueCount}</td>
        <td>${a.paidOrders}</td>
        <td>${fmtYuan(a.contributedIncome)}</td>
        <td>${a.joinTime}</td>
      </tr>`).join('');
    } catch (err) {
      showToast('代理列表加载失败', 'error');
    }
  }

  // ============================================
  // Orders
  // ============================================
  async function renderOrders(container) {
    container.innerHTML = `
      <div class="card">
        <div class="card-title">
          <span>与我相关的订单</span>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>订单号</th>
                <th>时间</th>
                <th>门店</th>
                <th>金额</th>
                <th>我的抽成</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody id="ordersTbody">
              <tr><td colspan="6"><div class="loading">加载中</div></td></tr>
            </tbody>
          </table>
        </div>
        <div class="pagination" id="ordersPagination"></div>
      </div>
    `;

    ordersPage = 1;
    loadOrders();
  }

  async function loadOrders() {
    const tbody = document.getElementById('ordersTbody');
    if (!tbody) return;

    try {
      const params = new URLSearchParams({ page: ordersPage, limit: PAGE_SIZE });
      const data = await api('/agent/orders?' + params.toString());
      if (!data) return;

      const list = data.list || [];
      ordersTotal = data.total || 0;

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><p>暂无订单数据</p></div></td></tr>';
        renderOrdersPagination();
        return;
      }

      const statusMap = {
        paid: { label: '已支付', class: 'badge-success' },
        settled: { label: '已结算', class: 'badge-info' },
        refunded: { label: '已退款', class: 'badge-danger' },
        pending: { label: '待支付', class: 'badge-warning' }
      };

      tbody.innerHTML = list.map(o => {
        const s = statusMap[o.status] || { label: o.status, class: 'badge-muted' };
        return `<tr>
          <td>${o.orderNo}</td>
          <td>${o.time}</td>
          <td>${o.venueName}</td>
          <td>${fmtYuan(o.amount)}</td>
          <td>${fmtYuan(o.myIncome)}</td>
          <td><span class="badge ${s.class}">${s.label}</span></td>
        </tr>`;
      }).join('');

      renderOrdersPagination();
    } catch (err) {
      tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state">加载失败</div></td></tr>';
    }
  }

  function renderOrdersPagination() {
    const el = document.getElementById('ordersPagination');
    if (!el) return;

    const totalPages = Math.ceil(ordersTotal / PAGE_SIZE);
    if (totalPages <= 1) {
      el.innerHTML = `<span>共 ${ordersTotal} 条</span>`;
      return;
    }

    let buttons = '';
    buttons += `<button ${ordersPage <= 1 ? 'disabled' : ''} onclick="window._pnApp.goOrdersPage(${ordersPage - 1})">上一页</button>`;

    const maxButtons = 5;
    let startP = Math.max(1, ordersPage - 2);
    let endP = Math.min(totalPages, startP + maxButtons - 1);
    startP = Math.max(1, endP - maxButtons + 1);

    for (let i = startP; i <= endP; i++) {
      buttons += `<button class="${i === ordersPage ? 'active' : ''}" onclick="window._pnApp.goOrdersPage(${i})">${i}</button>`;
    }

    buttons += `<button ${ordersPage >= totalPages ? 'disabled' : ''} onclick="window._pnApp.goOrdersPage(${ordersPage + 1})">下一页</button>`;

    el.innerHTML = `<span>共 ${ordersTotal} 条，第 ${ordersPage}/${totalPages} 页</span><div class="pagination-buttons">${buttons}</div>`;
  }

  function goOrdersPage(page) {
    ordersPage = page;
    loadOrders();
  }

  // ============================================
  // Withdrawals
  // ============================================
  async function renderWithdrawals(container) {
    container.innerHTML = `
      <div class="card">
        <div class="card-title">
          <span>申请提现</span>
        </div>
        <div class="withdraw-row">
          <div>
            <div class="stat-label">可提余额</div>
            <div class="stat-value gold" id="wdAvailable">--</div>
          </div>
          <div class="withdraw-form">
            <input type="number" id="wdAmount" placeholder="提现金额（元）" min="0.01" step="0.01">
            <button class="btn btn-gold btn-sm" id="wdBtn">申请提现</button>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="card-title">
          <span>提现记录</span>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>金额</th>
                <th>状态</th>
                <th>申请时间</th>
                <th>打款时间</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody id="withdrawalsTbody">
              <tr><td colspan="5"><div class="loading">加载中</div></td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;

    document.getElementById('wdBtn').addEventListener('click', confirmWithdraw);

    loadWithdrawAvailable();
    loadWithdrawals();
  }

  async function loadWithdrawAvailable() {
    try {
      const data = await api('/agent/summary');
      if (data && !data.error) {
        document.getElementById('wdAvailable').textContent = fmtYuan(data.available);
        document.getElementById('wdAvailable').dataset.fen = data.available;
      }
    } catch (err) {
      // 余额加载失败不阻塞列表
    }
  }

  async function loadWithdrawals() {
    const tbody = document.getElementById('withdrawalsTbody');
    if (!tbody) return;

    try {
      const data = await api('/agent/withdrawals');
      if (!data) return;
      const list = data.list || [];

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="5"><div class="empty-state"><p>暂无提现记录</p></div></td></tr>';
        return;
      }

      const statusMap = {
        pending: { label: '待处理', class: 'badge-warning' },
        paid: { label: '已打款', class: 'badge-success' },
        rejected: { label: '已拒绝', class: 'badge-danger' }
      };

      tbody.innerHTML = list.map(w => {
        const s = statusMap[w.status] || { label: w.status, class: 'badge-muted' };
        return `<tr>
          <td>${fmtYuan(w.amount)}</td>
          <td><span class="badge ${s.class}">${s.label}</span></td>
          <td>${w.time}</td>
          <td>${w.paidAt}</td>
          <td>${w.note}</td>
        </tr>`;
      }).join('');
    } catch (err) {
      tbody.innerHTML = '<tr><td colspan="5"><div class="empty-state">加载失败</div></td></tr>';
    }
  }

  async function confirmWithdraw() {
    const input = document.getElementById('wdAmount');
    const yuan = parseFloat(input.value);

    if (!yuan || yuan <= 0) {
      showToast('请输入正确的提现金额', 'error');
      return;
    }

    const amount = Math.round(yuan * 100); // 元 → 分
    const available = parseInt(document.getElementById('wdAvailable').dataset.fen || '0');
    if (amount > available) {
      showToast('提现金额超过可提余额', 'error');
      return;
    }

    try {
      const data = await api('/agent/withdraw', {
        method: 'POST',
        body: JSON.stringify({ amount })
      });
      if (data && !data.error) {
        showToast(data.message || '提现申请已提交', 'success');
        input.value = '';
        loadWithdrawAvailable();
        loadWithdrawals();
      } else {
        showToast((data && (data.message || data.error)) || '申请失败', 'error');
      }
    } catch (err) {
      showToast('网络错误', 'error');
    }
  }

  // ---- Utils ----
  // 后端金额单位为分，前端展示为元
  function fmtYuan(fen) {
    return '¥' + Number((fen || 0) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  // ---- Expose for inline onclick ----
  window._pnApp = {
    goOrdersPage
  };

  // ---- Start ----
  init();
})();
