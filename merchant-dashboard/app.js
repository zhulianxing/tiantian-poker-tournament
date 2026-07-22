/* ============================================
   天天扑克锦标赛 Merchant Dashboard - App Logic
   ============================================ */

(function () {
  'use strict';

  // ---- Config ----
  const API_BASE = '/merchant/api/v1';
  const PAGE_SIZE = 15;
  const IS_DEMO = localStorage.getItem('pn_merchant_token') === 'demo-token';

  // ---- State ----
  let currentRoute = 'dashboard';
  let ordersPage = 1;
  let ordersTotal = 0;
  let ordersData = [];
  let devicesData = [];
  let settlementsData = [];
  let refundsData = [];
  let boundAgent = null;

  // ---- Init ----
  function init() {
    const token = localStorage.getItem('pn_merchant_token');
    if (!token) {
      window.location.href = 'login.html';
      return;
    }

    // Show merchant info
    const info = JSON.parse(localStorage.getItem('pn_merchant_info') || '{}');
    document.getElementById('merchantInfo').textContent = info.name || info.username || '商户';

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

    // Modal close on overlay click
    document.getElementById('modalOverlay').addEventListener('click', (e) => {
      if (e.target === e.currentTarget) closeModal();
    });

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
      dashboard: '数据看板',
      orders: '订单管理',
      devices: '设备管理',
      settlements: '结算管理',
      refunds: '退款管理'
    };
    document.getElementById('pageTitle').textContent = titles[route] || '数据看板';

    // Close sidebar on mobile
    document.getElementById('sidebar').classList.remove('open');

    // Render
    const content = document.getElementById('contentArea');
    content.innerHTML = '';

    switch (route) {
      case 'dashboard': renderDashboard(content); break;
      case 'orders': renderOrders(content); break;
      case 'devices': renderDevices(content); break;
      case 'settlements': renderSettlements(content); break;
      case 'refunds': renderRefunds(content); break;
      default: renderDashboard(content);
    }
  }

  // ---- API ----
  async function api(path, options) {
    const token = localStorage.getItem('pn_merchant_token');
    const opts = options || {};
    opts.headers = opts.headers || {};
    opts.headers['Authorization'] = 'Bearer ' + token;
    if (opts.body && !opts.headers['Content-Type']) {
      opts.headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(API_BASE + path, opts);
    if (res.status === 401) {
      localStorage.removeItem('pn_merchant_token');
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

  // ---- Modal ----
  function openModal(title, bodyHTML, actionsHTML) {
    const overlay = document.getElementById('modalOverlay');
    const modal = document.getElementById('modalContent');
    modal.innerHTML = `<div class="modal-title">${title}</div>${bodyHTML}<div class="modal-actions">${actionsHTML || ''}</div>`;
    overlay.classList.add('show');
  }

  function closeModal() {
    document.getElementById('modalOverlay').classList.remove('show');
  }

  // ---- Logout ----
  function logout() {
    localStorage.removeItem('pn_merchant_token');
    localStorage.removeItem('pn_merchant_info');
    window.location.href = 'login.html';
  }

  // ============================================
  // Dashboard
  // ============================================
  async function renderDashboard(container) {
    container.innerHTML = `
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">今日订单数</div>
          <div class="stat-value" id="todayOrders">--</div>
          <div class="stat-sub" id="todayOrdersSub">加载中...</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">今日流水总额</div>
          <div class="stat-value gold" id="todayRevenue">--</div>
          <div class="stat-sub" id="todayRevenueSub">¥</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">待结算金额</div>
          <div class="stat-value warning" id="pendingSettlement">--</div>
          <div class="stat-sub" id="pendingSettlementSub">¥</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">本周赛事场次</div>
          <div class="stat-value" id="weekMatches">--</div>
          <div class="stat-sub" id="weekMatchesSub">场</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">本周客流量</div>
          <div class="stat-value" id="weekTraffic">--</div>
          <div class="stat-sub" id="weekTrafficSub">人次</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">在线设备数</div>
          <div class="stat-value success" id="onlineDevices">--</div>
          <div class="stat-sub" id="onlineDevicesSub">台</div>
        </div>
      </div>
      <div id="agentCardWrap"></div>
      <div class="card">
        <div class="card-title">最近 7 天订单趋势</div>
        <div class="chart-container">
          <canvas id="chartCanvas"></canvas>
        </div>
      </div>
    `;

    // Load data
    if (IS_DEMO) {
      fillDashboardDemo();
      drawChart(getDemoChartData());
    } else {
      try {
        const data = await api('/dashboard/summary');
        if (data) {
          document.getElementById('todayOrders').textContent = data.todayOrders ?? 0;
          document.getElementById('todayOrdersSub').textContent = '较昨日 ' + (data.ordersChange || '+0%');
          document.getElementById('todayRevenue').textContent = '¥' + formatMoney(data.todayRevenue || 0);
          document.getElementById('todayRevenueSub').textContent = '较昨日 ' + (data.revenueChange || '+0%');
          document.getElementById('pendingSettlement').textContent = '¥' + formatMoney(data.pendingSettlement || 0);
          document.getElementById('weekMatches').textContent = data.weekMatches ?? 0;
          document.getElementById('weekTraffic').textContent = data.weekTraffic ?? 0;
          document.getElementById('onlineDevices').textContent = data.onlineDevices ?? 0;
          boundAgent = data.boundAgent || null;
        }
        drawChart(data && data.chartData ? data.chartData : getDemoChartData());
      } catch (err) {
        showToast('数据加载失败', 'error');
        fillDashboardDemo();
        drawChart(getDemoChartData());
      }
    }
    renderAgentCard();
  }

  // ---- 绑定代理卡片 ----
  function renderAgentCard() {
    const wrap = document.getElementById('agentCardWrap');
    if (!wrap) return;

    if (boundAgent) {
      wrap.innerHTML = `
        <div class="card">
          <div class="card-title"><span>绑定代理</span></div>
          <div class="agent-bound">
            <span class="agent-bound-name">${boundAgent.name}</span>
            <span class="agent-bound-code">邀请码：${boundAgent.code}</span>
          </div>
        </div>
      `;
      return;
    }

    wrap.innerHTML = `
      <div class="card">
        <div class="card-title"><span>绑定代理</span></div>
        <div class="agent-bind-row">
          <div class="form-group">
            <label>代理邀请码</label>
            <input type="text" id="agentInviteCode" placeholder="请输入代理邀请码">
          </div>
          <button class="btn btn-gold btn-sm" id="bindAgentBtn">绑定</button>
        </div>
      </div>
    `;
    document.getElementById('bindAgentBtn').addEventListener('click', bindAgent);
  }

  async function bindAgent() {
    const inviteCode = (document.getElementById('agentInviteCode').value || '').trim();
    if (!inviteCode) {
      showToast('请输入邀请码', 'error');
      return;
    }

    if (IS_DEMO) {
      boundAgent = { name: 'Demo代理', code: inviteCode };
      renderAgentCard();
      showToast('代理绑定成功', 'success');
      return;
    }

    try {
      const data = await api('/merchant/bind-agent', {
        method: 'POST',
        body: JSON.stringify({ inviteCode })
      });
      if (data && data.ok) {
        boundAgent = data.agent;
        renderAgentCard();
        showToast('代理绑定成功', 'success');
      } else if (data && data.agent) {
        // 已绑定过（409），展示当前代理
        boundAgent = data.agent;
        renderAgentCard();
        showToast('已绑定代理：' + data.agent.name, 'error');
      } else {
        showToast((data && data.error) || '绑定失败', 'error');
      }
    } catch (err) {
      showToast('网络错误', 'error');
    }
  }

  function fillDashboardDemo() {
    document.getElementById('todayOrders').textContent = '47';
    document.getElementById('todayOrdersSub').textContent = '较昨日 +12%';
    document.getElementById('todayRevenue').textContent = '¥3,290';
    document.getElementById('todayRevenueSub').textContent = '较昨日 +8.5%';
    document.getElementById('pendingSettlement').textContent = '¥18,560';
    document.getElementById('pendingSettlementSub').textContent = '3个周期待结算';
    document.getElementById('weekMatches').textContent = '28';
    document.getElementById('weekMatchesSub').textContent = '场';
    document.getElementById('weekTraffic').textContent = '312';
    document.getElementById('weekTrafficSub').textContent = '人次';
    document.getElementById('onlineDevices').textContent = '8';
    document.getElementById('onlineDevicesSub').textContent = '/ 10 台';
  }

  function getDemoChartData() {
    return [
      { date: '07/12', orders: 32, revenue: 2240 },
      { date: '07/13', orders: 38, revenue: 2660 },
      { date: '07/14', orders: 45, revenue: 3150 },
      { date: '07/15', orders: 41, revenue: 2870 },
      { date: '07/16', orders: 52, revenue: 3640 },
      { date: '07/17', orders: 39, revenue: 2730 },
      { date: '07/18', orders: 47, revenue: 3290 }
    ];
  }

  // ---- Chart ----
  function drawChart(data) {
    const canvas = document.getElementById('chartCanvas');
    if (!canvas) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);

    const W = rect.width;
    const H = rect.height;
    const padding = { top: 20, right: 20, bottom: 30, left: 50 };
    const chartW = W - padding.left - padding.right;
    const chartH = H - padding.top - padding.bottom;

    const maxVal = Math.max(...data.map(d => d.orders), 10);
    const barW = chartW / data.length * 0.6;
    const gap = chartW / data.length * 0.4;

    // Y axis labels
    ctx.fillStyle = 'rgba(255,255,255,0.4)';
    ctx.font = '11px -apple-system';
    ctx.textAlign = 'right';
    const steps = 4;
    for (let i = 0; i <= steps; i++) {
      const val = Math.round(maxVal * i / steps);
      const y = padding.top + chartH - (chartH * i / steps);
      ctx.fillText(val, padding.left - 8, y + 3);
      // Grid line
      ctx.strokeStyle = 'rgba(255,255,255,0.05)';
      ctx.beginPath();
      ctx.moveTo(padding.left, y);
      ctx.lineTo(W - padding.right, y);
      ctx.stroke();
    }

    // Bars
    data.forEach((d, i) => {
      const x = padding.left + (chartW / data.length) * i + gap / 2;
      const barH = (d.orders / maxVal) * chartH;
      const y = padding.top + chartH - barH;

      // Bar gradient
      const grad = ctx.createLinearGradient(0, y, 0, y + barH);
      grad.addColorStop(0, '#FFD700');
      grad.addColorStop(1, 'rgba(255,215,0,0.3)');
      ctx.fillStyle = grad;

      // Rounded bar
      const r = 4;
      ctx.beginPath();
      ctx.moveTo(x + r, y);
      ctx.lineTo(x + barW - r, y);
      ctx.quadraticCurveTo(x + barW, y, x + barW, y + r);
      ctx.lineTo(x + barW, y + barH);
      ctx.lineTo(x, y + barH);
      ctx.lineTo(x, y + r);
      ctx.quadraticCurveTo(x, y, x + r, y);
      ctx.fill();

      // Value
      ctx.fillStyle = 'rgba(255,255,255,0.7)';
      ctx.font = '11px -apple-system';
      ctx.textAlign = 'center';
      ctx.fillText(d.orders, x + barW / 2, y - 6);

      // X label
      ctx.fillStyle = 'rgba(255,255,255,0.4)';
      ctx.fillText(d.date, x + barW / 2, H - padding.bottom + 18);
    });
  }

  // ============================================
  // Orders
  // ============================================
  async function renderOrders(container) {
    container.innerHTML = `
      <div class="card">
        <div class="card-title">
          <span>订单列表</span>
          <button class="btn btn-outline btn-sm" id="exportCSV">导出 CSV</button>
        </div>
        <div class="filters">
          <div class="filter-group">
            <label>开始日期</label>
            <input type="date" id="filterDateStart">
          </div>
          <div class="filter-group">
            <label>结束日期</label>
            <input type="date" id="filterDateEnd">
          </div>
          <div class="filter-group">
            <label>状态</label>
            <select id="filterStatus">
              <option value="">全部</option>
              <option value="paid">已支付</option>
              <option value="settled">已结算</option>
              <option value="refunded">已退款</option>
              <option value="pending">待支付</option>
            </select>
          </div>
          <div class="filter-group">
            <label>搜索</label>
            <input type="text" id="filterSearch" placeholder="订单号/账号">
          </div>
          <button class="btn btn-gold btn-sm" id="filterBtn">查询</button>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>订单号</th>
                <th>时间</th>
                <th>付费账号</th>
                <th>金额</th>
                <th>70%分成</th>
                <th>赛事编号</th>
                <th>设备SN</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody id="ordersTbody">
              <tr><td colspan="8"><div class="loading">加载中</div></td></tr>
            </tbody>
          </table>
        </div>
        <div class="pagination" id="ordersPagination"></div>
      </div>
    `;

    document.getElementById('filterBtn').addEventListener('click', () => {
      ordersPage = 1;
      loadOrders();
    });

    document.getElementById('exportCSV').addEventListener('click', exportOrdersCSV);

    loadOrders();
  }

  async function loadOrders() {
    const tbody = document.getElementById('ordersTbody');
    if (!tbody) return;

    const dateStart = document.getElementById('filterDateStart')?.value || '';
    const dateEnd = document.getElementById('filterDateEnd')?.value || '';
    const status = document.getElementById('filterStatus')?.value || '';
    const search = document.getElementById('filterSearch')?.value || '';

    if (IS_DEMO) {
      ordersData = generateDemoOrders();
      if (status) ordersData = ordersData.filter(o => o.status === status);
      if (search) ordersData = ordersData.filter(o => o.orderNo.includes(search) || o.account.includes(search));
      ordersTotal = ordersData.length;
      renderOrdersTable();
      return;
    }

    try {
      const params = new URLSearchParams({
        page: ordersPage,
        pageSize: PAGE_SIZE
      });
      if (dateStart) params.set('dateStart', dateStart);
      if (dateEnd) params.set('dateEnd', dateEnd);
      if (status) params.set('status', status);
      if (search) params.set('search', search);

      const data = await api('/orders?' + params.toString());
      if (data) {
        ordersData = data.list || data.data || [];
        ordersTotal = data.total || 0;
        renderOrdersTable();
      }
    } catch (err) {
      tbody.innerHTML = '<tr><td colspan="8"><div class="empty-state">加载失败</div></td></tr>';
    }
  }

  function renderOrdersTable() {
    const tbody = document.getElementById('ordersTbody');
    if (!tbody) return;

    if (!ordersData.length) {
      tbody.innerHTML = '<tr><td colspan="8"><div class="empty-state"><p>暂无订单数据</p></div></td></tr>';
      renderOrdersPagination();
      return;
    }

    const start = (ordersPage - 1) * PAGE_SIZE;
    const end = start + PAGE_SIZE;
    const pageData = ordersData.slice(start, end);

    const statusMap = {
      paid: { label: '已支付', class: 'badge-success' },
      settled: { label: '已结算', class: 'badge-info' },
      refunded: { label: '已退款', class: 'badge-danger' },
      pending: { label: '待支付', class: 'badge-warning' }
    };

    tbody.innerHTML = pageData.map(o => {
      const s = statusMap[o.status] || { label: o.status, class: 'badge-muted' };
      return `<tr>
        <td>${o.orderNo}</td>
        <td>${o.time}</td>
        <td>${o.account}</td>
        <td>¥${formatMoney(o.amount)}</td>
        <td>¥${formatMoney(o.amount * 0.7)}</td>
        <td>${o.matchId || '--'}</td>
        <td>${o.deviceSN || '--'}</td>
        <td><span class="badge ${s.class}">${s.label}</span></td>
      </tr>`;
    }).join('');

    renderOrdersPagination();
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
    if (IS_DEMO) {
      renderOrdersTable();
    } else {
      loadOrders();
    }
  }

  function exportOrdersCSV() {
    let csv = '\uFEFF订单号,时间,付费账号,金额,70%分成,赛事编号,设备SN,状态\n';
    const statusLabel = { paid: '已支付', settled: '已结算', refunded: '已退款', pending: '待支付' };

    const data = IS_DEMO ? ordersData : ordersData;
    data.forEach(o => {
      csv += `${o.orderNo},${o.time},${o.account},${o.amount},${(o.amount * 0.7).toFixed(2)},${o.matchId || ''},${o.deviceSN || ''},${statusLabel[o.status] || o.status}\n`;
    });

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `orders_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('CSV 导出成功', 'success');
  }

  function generateDemoOrders() {
    const orders = [];
    const statuses = ['paid', 'paid', 'paid', 'settled', 'settled', 'pending', 'refunded'];
    const accounts = ['player_001', 'player_002', 'poker_king', 'allin_master', 'bluff_pro', 'chip_leader', 'river_rat', 'slow_play'];
    for (let i = 0; i < 87; i++) {
      const d = new Date();
      d.setDate(d.getDate() - Math.floor(Math.random() * 7));
      const time = d.toISOString().slice(0, 16).replace('T', ' ');
      const amount = [30, 50, 50, 100, 100, 100, 200][Math.floor(Math.random() * 7)];
      orders.push({
        orderNo: 'PN' + String(20240718000000 + i * 137),
        time,
        account: accounts[Math.floor(Math.random() * accounts.length)],
        amount,
        matchId: 'M' + String(1000 + Math.floor(Math.random() * 50)),
        deviceSN: 'SN-' + String(1000 + Math.floor(Math.random() * 10)),
        status: statuses[Math.floor(Math.random() * statuses.length)]
      });
    }
    return orders.sort((a, b) => b.time.localeCompare(a.time));
  }

  // ============================================
  // Devices
  // ============================================
  async function renderDevices(container) {
    container.innerHTML = `
      <div class="card">
        <div class="card-title">
          <span>设备列表</span>
          <button class="btn btn-gold btn-sm" id="bindDeviceBtn">+ 绑定新设备</button>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>设备SN</th>
                <th>绑定场馆</th>
                <th>桌号</th>
                <th>状态</th>
                <th>绑定时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody id="devicesTbody">
              <tr><td colspan="6"><div class="loading">加载中</div></td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;

    document.getElementById('bindDeviceBtn').addEventListener('click', showBindDeviceModal);

    if (IS_DEMO) {
      devicesData = generateDemoDevices();
      renderDevicesTable();
    } else {
      try {
        const data = await api('/devices');
        if (data) {
          devicesData = data.list || data.data || [];
          renderDevicesTable();
        }
      } catch (err) {
        showToast('设备列表加载失败', 'error');
      }
    }
  }

  function renderDevicesTable() {
    const tbody = document.getElementById('devicesTbody');
    if (!tbody) return;

    if (!devicesData.length) {
      tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><p>暂无绑定设备</p></div></td></tr>';
      return;
    }

    const statusMap = {
      online: { label: '在线', class: 'badge-success' },
      offline: { label: '离线', class: 'badge-muted' },
      maintenance: { label: '维护中', class: 'badge-warning' }
    };

    tbody.innerHTML = devicesData.map(d => {
      const s = statusMap[d.status] || { label: d.status, class: 'badge-muted' };
      return `<tr>
        <td>${d.sn}</td>
        <td>${d.venue || '--'}</td>
        <td>${d.tableNo || '--'}</td>
        <td><span class="badge ${s.class}">${s.label}</span></td>
        <td>${d.bindTime || '--'}</td>
        <td><button class="btn btn-danger btn-sm" onclick="window._pnApp.unbindDevice('${d.sn}')">解绑</button></td>
      </tr>`;
    }).join('');
  }

  function showBindDeviceModal() {
    openModal('绑定新设备', `
      <div class="form-group">
        <label>设备 SN</label>
        <input type="text" id="bindSN" placeholder="请输入设备序列号">
      </div>
      <div class="form-group">
        <label>桌号</label>
        <input type="text" id="bindTableNo" placeholder="请输入桌号（如：A1）">
      </div>
    `, `<button class="btn btn-outline btn-sm" onclick="window._pnApp.closeModal()">取消</button><button class="btn btn-gold btn-sm" onclick="window._pnApp.confirmBindDevice()">确认绑定</button>`);
  }

  async function confirmBindDevice() {
    const sn = document.getElementById('bindSN').value.trim();
    const tableNo = document.getElementById('bindTableNo').value.trim();

    if (!sn || !tableNo) {
      showToast('请填写完整信息', 'error');
      return;
    }

    if (IS_DEMO) {
      devicesData.unshift({
        sn,
        venue: 'Demo场馆',
        tableNo,
        status: 'online',
        bindTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
      });
      renderDevicesTable();
      closeModal();
      showToast('设备绑定成功', 'success');
      return;
    }

    try {
      const data = await api('/devices/bind', {
        method: 'POST',
        body: JSON.stringify({ sn, tableNo })
      });
      if (data && !data.error) {
        closeModal();
        showToast('设备绑定成功', 'success');
        renderDevices(null);
      } else {
        showToast(data.message || '绑定失败', 'error');
      }
    } catch (err) {
      showToast('网络错误', 'error');
    }
  }

  async function unbindDevice(sn) {
    if (!confirm(`确定解绑设备 ${sn} 吗？`)) return;

    if (IS_DEMO) {
      devicesData = devicesData.filter(d => d.sn !== sn);
      renderDevicesTable();
      showToast('设备已解绑', 'success');
      return;
    }

    try {
      const data = await api('/devices/unbind', {
        method: 'POST',
        body: JSON.stringify({ sn })
      });
      if (data && !data.error) {
        showToast('设备已解绑', 'success');
        renderDevices(null);
      } else {
        showToast(data.message || '解绑失败', 'error');
      }
    } catch (err) {
      showToast('网络错误', 'error');
    }
  }

  function generateDemoDevices() {
    return [
      { sn: 'SN-1001', venue: '旗舰店', tableNo: 'A1', status: 'online', bindTime: '2024-06-01 10:30:00' },
      { sn: 'SN-1002', venue: '旗舰店', tableNo: 'A2', status: 'online', bindTime: '2024-06-01 10:32:00' },
      { sn: 'SN-1003', venue: '旗舰店', tableNo: 'B1', status: 'online', bindTime: '2024-06-02 14:20:00' },
      { sn: 'SN-1004', venue: '旗舰店', tableNo: 'B2', status: 'offline', bindTime: '2024-06-02 14:25:00' },
      { sn: 'SN-1005', venue: '旗舰店', tableNo: 'C1', status: 'online', bindTime: '2024-06-05 09:15:00' },
      { sn: 'SN-1006', venue: '旗舰店', tableNo: 'C2', status: 'maintenance', bindTime: '2024-06-05 09:20:00' },
      { sn: 'SN-1007', venue: '旗舰店', tableNo: 'D1', status: 'online', bindTime: '2024-06-10 16:00:00' },
      { sn: 'SN-1008', venue: '旗舰店', tableNo: 'D2', status: 'online', bindTime: '2024-06-10 16:05:00' }
    ];
  }

  // ============================================
  // Settlements
  // ============================================
  async function renderSettlements(container) {
    container.innerHTML = `
      <div class="card">
        <div class="card-title">
          <span>结算周期</span>
          <button class="btn btn-gold btn-sm" id="withdrawBtn">申请提现</button>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>结算周期</th>
                <th>订单数</th>
                <th>总额</th>
                <th>70%分成</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody id="settlementsTbody">
              <tr><td colspan="6"><div class="loading">加载中</div></td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;

    document.getElementById('withdrawBtn').addEventListener('click', showWithdrawModal);

    if (IS_DEMO) {
      settlementsData = generateDemoSettlements();
      renderSettlementsTable();
    } else {
      try {
        const data = await api('/settlements');
        if (data) {
          settlementsData = data.list || data.data || [];
          renderSettlementsTable();
        }
      } catch (err) {
        showToast('结算数据加载失败', 'error');
      }
    }
  }

  function renderSettlementsTable() {
    const tbody = document.getElementById('settlementsTbody');
    if (!tbody) return;

    if (!settlementsData.length) {
      tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><p>暂无结算记录</p></div></td></tr>';
      return;
    }

    const statusMap = {
      pending: { label: '待结算', class: 'badge-warning' },
      processing: { label: '处理中', class: 'badge-info' },
      completed: { label: '已结算', class: 'badge-success' },
      failed: { label: '已失败', class: 'badge-danger' }
    };

    tbody.innerHTML = settlementsData.map(s => {
      const st = statusMap[s.status] || { label: s.status, class: 'badge-muted' };
      return `<tr>
        <td>${s.period}</td>
        <td>${s.orderCount}</td>
        <td>¥${formatMoney(s.totalAmount)}</td>
        <td>¥${formatMoney(s.merchantShare)}</td>
        <td><span class="badge ${st.class}">${st.label}</span></td>
        <td>${s.voucherUrl ? `<button class="btn btn-outline btn-sm" onclick="window._pnApp.viewVoucher('${s.voucherUrl}')">查看凭证</button>` : '--'}</td>
      </tr>`;
    }).join('');
  }

  function showWithdrawModal() {
    const pending = settlementsData.filter(s => s.status === 'pending');
    const total = pending.reduce((sum, s) => sum + s.merchantShare, 0);

    openModal('申请提现', `
      <div style="margin-bottom: 16px;">
        <div style="font-size: 12px; color: var(--text-muted); margin-bottom: 6px;">可提现金额</div>
        <div style="font-size: 28px; font-weight: 700; color: var(--gold);">¥${formatMoney(total)}</div>
      </div>
      <div class="form-group">
        <label>提现至（银行卡/支付宝）</label>
        <input type="text" id="withdrawTarget" placeholder="请输入收款账号">
      </div>
      <div class="form-group">
        <label>备注</label>
        <input type="text" id="withdrawNote" placeholder="选填">
      </div>
    `, `<button class="btn btn-outline btn-sm" onclick="window._pnApp.closeModal()">取消</button><button class="btn btn-gold btn-sm" onclick="window._pnApp.confirmWithdraw()">提交申请</button>`);
  }

  async function confirmWithdraw() {
    const target = document.getElementById('withdrawTarget').value.trim();
    if (!target) {
      showToast('请输入收款账号', 'error');
      return;
    }

    if (IS_DEMO) {
      settlementsData.forEach(s => {
        if (s.status === 'pending') s.status = 'processing';
      });
      renderSettlementsTable();
      closeModal();
      showToast('提现申请已提交', 'success');
      return;
    }

    try {
      const data = await api('/settlements/withdraw', {
        method: 'POST',
        body: JSON.stringify({ target, note: document.getElementById('withdrawNote').value })
      });
      if (data && !data.error) {
        closeModal();
        showToast('提现申请已提交', 'success');
        renderSettlements(null);
      } else {
        showToast(data.message || '申请失败', 'error');
      }
    } catch (err) {
      showToast('网络错误', 'error');
    }
  }

  function viewVoucher(url) {
    openModal('转账凭证', `<div style="text-align:center;"><img src="${url}" style="max-width:100%;border-radius:8px;" alt="凭证"></div>`, `<button class="btn btn-outline btn-sm" onclick="window._pnApp.closeModal()">关闭</button>`);
  }

  function generateDemoSettlements() {
    return [
      { period: '2024-W28', orderCount: 47, totalAmount: 3290, merchantShare: 2303, status: 'pending', voucherUrl: null },
      { period: '2024-W27', orderCount: 52, totalAmount: 3640, merchantShare: 2548, status: 'processing', voucherUrl: null },
      { period: '2024-W26', orderCount: 38, totalAmount: 2660, merchantShare: 1862, status: 'completed', voucherUrl: 'https://via.placeholder.com/400x300/1a1a1a/FFD700?text=Voucher+W26' },
      { period: '2024-W25', orderCount: 45, totalAmount: 3150, merchantShare: 2205, status: 'completed', voucherUrl: 'https://via.placeholder.com/400x300/1a1a1a/FFD700?text=Voucher+W25' },
      { period: '2024-W24', orderCount: 41, totalAmount: 2870, merchantShare: 2009, status: 'completed', voucherUrl: null }
    ];
  }

  // ============================================
  // Refunds
  // ============================================
  async function renderRefunds(container) {
    container.innerHTML = `
      <div class="card">
        <div class="card-title">
          <span>退款列表</span>
          <button class="btn btn-gold btn-sm" id="refundBtn">发起退款</button>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>退款单号</th>
                <th>原订单号</th>
                <th>金额</th>
                <th>原因</th>
                <th>申请时间</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody id="refundsTbody">
              <tr><td colspan="6"><div class="loading">加载中</div></td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;

    document.getElementById('refundBtn').addEventListener('click', showRefundModal);

    if (IS_DEMO) {
      refundsData = generateDemoRefunds();
      renderRefundsTable();
    } else {
      try {
        const data = await api('/refunds');
        if (data) {
          refundsData = data.list || data.data || [];
          renderRefundsTable();
        }
      } catch (err) {
        showToast('退款列表加载失败', 'error');
      }
    }
  }

  function renderRefundsTable() {
    const tbody = document.getElementById('refundsTbody');
    if (!tbody) return;

    if (!refundsData.length) {
      tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><p>暂无退款记录</p></div></td></tr>';
      return;
    }

    const statusMap = {
      pending: { label: '待处理', class: 'badge-warning' },
      processing: { label: '处理中', class: 'badge-info' },
      completed: { label: '已退款', class: 'badge-success' },
      rejected: { label: '已拒绝', class: 'badge-danger' }
    };

    tbody.innerHTML = refundsData.map(r => {
      const s = statusMap[r.status] || { label: r.status, class: 'badge-muted' };
      return `<tr>
        <td>${r.refundNo}</td>
        <td>${r.orderNo}</td>
        <td>¥${formatMoney(r.amount)}</td>
        <td>${r.reason}</td>
        <td>${r.applyTime}</td>
        <td><span class="badge ${s.class}">${s.label}</span></td>
      </tr>`;
    }).join('');
  }

  function showRefundModal() {
    openModal('发起退款', `
      <div class="form-group">
        <label>订单号</label>
        <input type="text" id="refundOrderNo" placeholder="请输入要退款的订单号">
      </div>
      <div class="form-group">
        <label>退款原因</label>
        <input type="text" id="refundReason" placeholder="请输入退款原因">
      </div>
    `, `<button class="btn btn-outline btn-sm" onclick="window._pnApp.closeModal()">取消</button><button class="btn btn-gold btn-sm" onclick="window._pnApp.confirmRefund()">提交退款</button>`);
  }

  async function confirmRefund() {
    const orderNo = document.getElementById('refundOrderNo').value.trim();
    const reason = document.getElementById('refundReason').value.trim();

    if (!orderNo || !reason) {
      showToast('请填写完整信息', 'error');
      return;
    }

    if (IS_DEMO) {
      refundsData.unshift({
        refundNo: 'RF' + Date.now(),
        orderNo,
        amount: 50,
        reason,
        applyTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
        status: 'pending'
      });
      renderRefundsTable();
      closeModal();
      showToast('退款申请已提交', 'success');
      return;
    }

    try {
      const data = await api('/refunds/apply', {
        method: 'POST',
        body: JSON.stringify({ orderNo, reason })
      });
      if (data && !data.error) {
        closeModal();
        showToast('退款申请已提交', 'success');
        renderRefunds(null);
      } else {
        showToast(data.message || '退款失败', 'error');
      }
    } catch (err) {
      showToast('网络错误', 'error');
    }
  }

  function generateDemoRefunds() {
    return [
      { refundNo: 'RF20240718001', orderNo: 'PN20240718000137', amount: 50, reason: '设备故障', applyTime: '2024-07-18 09:30:00', status: 'pending' },
      { refundNo: 'RF20240717002', orderNo: 'PN20240717000274', amount: 100, reason: '赛事异常中断', applyTime: '2024-07-17 15:20:00', status: 'completed' },
      { refundNo: 'RF20240716003', orderNo: 'PN20240716000411', amount: 30, reason: '用户投诉', applyTime: '2024-07-16 18:45:00', status: 'completed' },
      { refundNo: 'RF20240715004', orderNo: 'PN20240715000548', amount: 50, reason: '误操作', applyTime: '2024-07-15 11:10:00', status: 'rejected' }
    ];
  }

  // ---- Utils ----
  function formatMoney(n) {
    return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  // ---- Expose for inline onclick ----
  window._pnApp = {
    goOrdersPage,
    closeModal,
    confirmBindDevice,
    unbindDevice,
    confirmWithdraw,
    viewVoucher,
    confirmRefund
  };

  // ---- Start ----
  init();
})();
