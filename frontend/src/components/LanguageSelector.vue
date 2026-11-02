<template>
  <el-dropdown class="language-selector" trigger="click" placement="bottom-end" @command="setLanguage" @visible-change="expanded=$event">
    <button type="button" class="language-toggle" aria-haspopup="menu" :aria-expanded="String(expanded)">
      {{$t('选择语言')}} <i class="el-icon-arrow-down" aria-hidden="true"/>
    </button>
    <el-dropdown-menu slot="dropdown" class="language-menu">
      <el-dropdown-item command="zh" :class="{'language-selected':languageState.locale==='zh'}">
        <span lang="zh-CN">中文</span><i v-if="languageState.locale==='zh'" class="el-icon-check" aria-hidden="true"/>
      </el-dropdown-item>
      <el-dropdown-item command="en" :class="{'language-selected':languageState.locale==='en'}">
        <span lang="en">English</span><i v-if="languageState.locale==='en'" class="el-icon-check" aria-hidden="true"/>
      </el-dropdown-item>
    </el-dropdown-menu>
  </el-dropdown>
</template>
<script>
import { languageState, setLanguage } from '../i18n'
export default {
  name: 'LanguageSelector',
  data() { return { languageState, expanded: false } },
  methods: { setLanguage }
}
</script>
