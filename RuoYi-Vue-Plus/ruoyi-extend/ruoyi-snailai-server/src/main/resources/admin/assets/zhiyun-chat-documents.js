/** SnailAI 调试对话的原始文件上传。文件使用后端会话和登录用户校验。 */
export function mountChatDocuments({ agentId, conversationId, baseURL, token, message }) {
  let disposed = false;
  let observer;
  let currentKey = '';
  let loading = false;
  let pending = [];
  const panel = document.createElement('div');
  panel.style.cssText = 'padding:0 12px 8px;font-size:12px;color:#666;line-height:1.6';
  const drafts = document.createElement('div');
  drafts.style.cssText = 'padding:0 12px;font-size:12px';
  const notice = document.createElement('div');
  notice.textContent = '文件可供当前会话按需解析；文档内图片暂不能直接进行视觉识别。';
  const list = document.createElement('div');
  list.style.cssText = 'display:flex;flex-wrap:wrap;gap:6px;margin-top:4px';
  panel.append(list);
  panel.hidden = true;
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.pdf,.docx,.xlsx,.pptx,.txt,.md,.csv';
  input.multiple = true;
  input.hidden = true;
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'agent-action-icon';
  button.title = '上传文件（PDF、Office、文本，单个不超过 10 MB）';
  button.setAttribute('aria-label', '上传文件');
  button.textContent = '📎';
  button.style.cssText = 'font-size:20px;cursor:pointer';
  button.addEventListener('click', () => input.click());
  const headers = () => ({ 'Snail-Ai-Auth': token() || '' });
  const query = (agent, conversation) => new URLSearchParams({ agentId: String(agent), conversationId: conversation });
  const endpoint = `${baseURL.replace(/\/$/, '')}/zhiyun/chat-documents`;
  async function result(response) {
    if (!response.ok) throw new Error(`请求失败（${response.status}）`);
    const body = await response.json();
    if (!body || (body.status !== undefined && body.status !== 1)) {
      throw new Error(body?.message || body?.msg || '请求失败');
    }
    return body.data;
  }
  function render(files, agent, conversation) {
    list.replaceChildren();
    panel.hidden = !files?.length;
    for (const file of files || []) {
      const link = document.createElement('button');
      link.type = 'button';
      link.textContent = `📄 ${file.name}`;
      link.title = '下载原始文件';
      link.style.cssText = 'border:1px solid #ddd;border-radius:5px;padding:3px 8px;background:#fff;cursor:pointer;max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap';
      link.addEventListener('click', async () => {
        try {
          const response = await fetch(`${endpoint}/${encodeURIComponent(file.id)}?${query(agent, conversation)}`, { headers: headers() });
          if (!response.ok) throw new Error(`下载失败（${response.status}）`);
          const url = URL.createObjectURL(await response.blob());
          const a = document.createElement('a');
          a.href = url;
          a.download = file.name;
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 60000);
        } catch (error) { message.error(error.message || '下载失败'); }
      });
      list.append(link);
    }
  }
  async function refreshKnowledge() {
    const agent = agentId(), conversation = conversationId();
    if (!agent || !conversation) return;
    try {
      const items = await result(await fetch(`${baseURL.replace(/\/$/, '')}/zhiyun/knowledge-drafts?${query(agent, conversation)}`, { headers: headers() }));
      if (disposed || agent !== agentId() || conversation !== conversationId()) return;
      drafts.replaceChildren();
      for (const item of items || []) {
        if (item.status === 'REJECTED') continue;
        const card = document.createElement('div');
        card.style.cssText = 'margin:8px 0;padding:10px;border:1px solid #ddd;border-radius:6px;white-space:pre-wrap;overflow-wrap:anywhere';
        const body = document.createElement('div');
        body.textContent = `知识草稿（${item.status === 'PENDING' ? '待确认' : item.status === 'SUBMITTED' ? (item.documentStatus === 3 ? '处理完成' : item.documentStatus === 4 ? '处理失败，请到知识库查看' : '已提交，等待处理') : item.status === 'FAILED' ? '提交失败' : '提交中'}）\n知识库编号：${item.ragId}\n标题：${item.title}\n来源：${item.source}\n完全相同条目：${item.exactDuplicateCount ?? 0}\n内容：${item.content}`;
        card.append(body);
        for (const [label, action] of (item.status === 'PENDING' ? [['确认提交', 'confirm'], ['拒绝', 'reject']] : [])) {
          const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
          button.style.cssText = 'margin:8px 8px 0 0;padding:4px 8px;cursor:pointer';
          button.addEventListener('click', async () => {
            if (action === 'confirm' && !window.confirm('确认将以上内容提交到所示知识库？')) return;
            button.disabled = true;
            try {
              const data = await result(await fetch(`${baseURL.replace(/\/$/, '')}/zhiyun/knowledge-drafts/${encodeURIComponent(item.id)}/${action}`, { method: 'POST', headers: headers() }));
              message.success(action === 'confirm' ? (data?.message || '已提交处理') : '已拒绝');
            } catch (error) { message.error(error.message || '操作失败'); }
            finally { await refreshKnowledge(); }
          });
          card.append(button);
        }
        drafts.append(card);
      }
    } catch (error) { /* 普通用户无管理权限，不展示知识写入卡片。 */ }
  }
  async function refresh() {
    const agent = agentId(), conversation = conversationId();
    if (!agent || !conversation) return;
    const key = `${agent}:${conversation}`;
    try {
      const files = await result(await fetch(`${endpoint}?${query(agent, conversation)}`, { headers: headers() }));
      if (!disposed && key === `${agentId()}:${conversationId()}`) { pending = pending.map(item => (files || []).find(f => f.id === item.id)).filter(Boolean); render(pending, agent, conversation); }
    } catch (error) { if (!disposed) message.error(`读取会话文件失败：${error.message}`); }
  }
  input.addEventListener('change', async () => {
    if (loading || !input.files?.length) return;
    const files = Array.from(input.files), agent = agentId(), conversation = conversationId();
    input.value = '';
    if (!agent || !conversation) { message.warning('请先打开智能体'); return; }
    loading = true;
    button.disabled = true;
    try {
      for (const file of files) {
        const form = new FormData();
        form.append('agentId', String(agent));
        form.append('conversationId', conversation);
        form.append('file', file);
        try {
          const uploaded = await result(await fetch(endpoint, { method: 'POST', headers: headers(), body: form }));
          pending.push(uploaded);
          message.success(`${file.name} 上传成功`);
        } catch (error) { message.error(`${file.name}：${error.message}`); }
      }
      await refresh();
    } finally { loading = false; button.disabled = false; }
  });
  function attach() {
    if (disposed) return;
    const actions = document.querySelector('.agent-input-actions');
    const row = document.querySelector('.agent-input-row');
    if (!actions || !row) return;
    if (!button.isConnected) actions.prepend(button, input);
    if (!panel.isConnected) row.after(panel);
    if (!drafts.isConnected) panel.after(drafts);
    const key = `${agentId()}:${conversationId()}`;
    if (key !== currentKey) { currentKey = key; refresh(); refreshKnowledge(); }
  }
  observer = new MutationObserver(attach);
  observer.observe(document.body, { childList: true, subtree: true });
  attach();
  return { refreshKnowledge, take() { const files = pending; pending = []; render([], agentId(), conversationId()); return files; }, download(file) { render([file], agentId(), conversationId()); list.querySelector('button')?.click(); render(pending, agentId(), conversationId()); }, dispose() { disposed = true; observer.disconnect(); button.remove(); input.remove(); panel.remove(); drafts.remove(); } };
}
