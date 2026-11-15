<script setup lang="ts">
/** 左侧编号导航仅定位表单分区，不限制用户的填写顺序。 */
defineProps<{ eyebrow: string; title: string; description: string; sections: { key: string; title: string; hint: string }[]; active: string }>()
defineEmits<{ select: [key: string] }>()
</script>

<template>
  <aside class="editor-section-nav" aria-label="表单分区导航">
    <div class="nav-intro"><span>{{ eyebrow }}</span><strong>{{ title }}</strong><p>{{ description }}</p></div>
    <nav><button v-for="(section, index) in sections" :key="section.key" type="button" :class="{ active: active === section.key }" :aria-current="active === section.key ? 'location' : undefined" @click="$emit('select', section.key)"><b>{{ String(index + 1).padStart(2, '0') }}</b><span>{{ section.title }}<small>{{ section.hint }}</small></span></button></nav>
    <div class="nav-footer">带 <span>*</span> 的项目为必填项</div>
  </aside>
</template>

<style scoped>
.editor-section-nav { display:flex; flex-direction:column; min-height:0; overflow:auto; padding:24px 14px 20px; background:#fff; border-right:1px solid #edf0f5; }
.nav-intro { padding:0 10px 18px; border-bottom:1px solid #edf0f5; }
.nav-intro > span { display:block; color:#527bc0; font-size:12px; font-weight:600; }
.nav-intro strong { display:block; margin-top:10px; color:#263854; font-size:16px; }
.nav-intro p { margin:10px 0 0; color:#8998ad; font-size:12px; line-height:1.6; }
nav { display:grid; gap:4px; margin-top:16px; }
nav button { display:flex; align-items:center; gap:12px; padding:10px; border:0; border-radius:8px; background:transparent; text-align:left; color:#344660; font-weight:600; cursor:pointer; }
nav button:hover,nav button:focus-visible,nav button.active { background:#eef4ff; }
nav b { display:grid; place-items:center; width:32px; height:32px; flex:none; border-radius:9px; background:#edf3ff; color:#3970ce; font-size:12px; }
nav small { display:block; margin-top:2px; color:#94a1b4; font-size:12px; font-weight:400; }
.nav-footer { margin:auto 10px 0; padding-top:16px; color:#91a0b4; font-size:12px; }
.nav-footer span { color:#e45c5c; }
@media(max-width:760px) { .editor-section-nav { padding:8px; border-right:0; border-bottom:1px solid #edf0f5; } .nav-intro,.nav-footer { display:none; } nav { display:flex; overflow-x:auto; margin:0; } nav button { white-space:nowrap; } nav small { display:none; } }
</style>
