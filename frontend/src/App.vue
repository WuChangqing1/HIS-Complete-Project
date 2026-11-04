<template>
  <div class="app-shell" v-if="user">
    <header class="topbar"><button class="brand brand-home" type="button" :aria-label="$t('返回工作台')" @click="goHome"><span class="brand-mark">H</span><div><strong>{{$t("东软云 HIS")}}</strong><small>{{$t("门诊管理系统")}}</small></div></button><div class="top-actions"><LanguageSelector/><el-dropdown trigger="click" @command="accountCommand"><span class="account-name"><i class="el-icon-user-solid"/> {{ $t(user.name) }} <i class="el-icon-arrow-down"/></span><el-dropdown-menu slot="dropdown"><el-dropdown-item disabled>{{$t(roleName(user.role))}}</el-dropdown-item><el-dropdown-item command="logout" divided>{{$t("安全退出")}}</el-dropdown-item></el-dropdown-menu></el-dropdown></div></header>
    <aside :class="['sidebar', { collapsed }]" :aria-label="$t('功能导航')"><div class="menu-items"><button v-for="item in visibleItems" :key="item.key" type="button" :class="['nav-item',{selected:page===item.key}]" :aria-label="$t(item.label)" :aria-current="page===item.key?'page':null" :title="$t(collapsed?item.label:'')" @click="page=item.key; mobileOpen=false"><i :class="page===item.key?item.selectedIcon:item.icon"/><span v-if="!collapsed">{{$t(item.label)}}</span></button></div><button class="collapse-toggle" @click="collapsed=!collapsed" :aria-label="$t(collapsed?'展开导航':'收起导航')"><i :class="collapsed?'el-icon-d-arrow-right':'el-icon-d-arrow-left'"/><span v-if="!collapsed">{{$t("收起导航")}}</span></button></aside>
    <main :class="['content',{compact:collapsed}]"><div class="page-heading"><div><h1>{{$t(pageTitle)}}</h1></div><el-tag size="small" effect="plain">{{$t(roleName(user.role))}}</el-tag></div><component :is="currentView" :key="page" :user="user" :meta="meta" @goto="page=$event"/></main>
  </div>
  <div v-else class="login-screen"><section class="login-card"><LanguageSelector class="login-language"/><div class="brand login-brand"><span class="brand-mark">H</span><div><strong>{{$t("东软云 HIS")}}</strong><small>{{$t("门诊管理系统")}}</small></div></div><h1>{{$t("欢迎登录")}}</h1><el-form @submit.native.prevent="login"><el-form-item><el-input v-model.trim="credentials.username" prefix-icon="el-icon-user" autocomplete="username" :placeholder="$t('用户名')" @keyup.enter.native="login"/></el-form-item><el-form-item><el-input v-model="credentials.password" prefix-icon="el-icon-lock" type="password" show-password autocomplete="current-password" :placeholder="$t('密码')" @keyup.enter.native="login"/></el-form-item><el-button type="primary" class="full" :loading="loading" @click="login">{{$t("登录系统")}}</el-button><el-button v-if="guestEnabled" class="full guest-entry" native-type="button" :loading="guestLoading" :disabled="loading" @click="enterGuest">{{$t("游客访问")}}</el-button></el-form></section></div>
</template>
<script>
import api from './api'
import LanguageSelector from './components/LanguageSelector.vue'
import Dashboard from './views/Dashboard.vue'
import Patients from './views/Patients.vue'
import Registrations from './views/Registrations.vue'
import Medical from './views/Medical.vue'
import Services from './views/Services.vue'
import Pharmacy from './views/Pharmacy.vue'
import Billing from './views/Billing.vue'
import Audit from './views/Audit.vue'
export default {
 name:'App',components:{LanguageSelector,Dashboard,Patients,Registrations,Medical,Services,Pharmacy,Billing,Audit},
 data(){return{user:null,guestEnabled:false,guestLoading:false,meta:{departments:[],levels:[],settlements:[],doctors:[]},page:'dashboard',collapsed:false,loading:false,credentials:{username:'',password:''},items:[
 {key:'dashboard',label:'工作台',icon:'el-icon-s-home',selectedIcon:'el-icon-s-home',component:'Dashboard',roles:[]},
 {key:'patients',label:'患者档案',icon:'el-icon-user',selectedIcon:'el-icon-user-solid',component:'Patients',roles:[]},
 {key:'registrations',label:'挂号管理',icon:'el-icon-date',selectedIcon:'el-icon-date',component:'Registrations',roles:['REGISTRAR','DOCTOR']},
 {key:'medical',label:'门诊医生',icon:'el-icon-document',selectedIcon:'el-icon-document-checked',component:'Medical',roles:['DOCTOR']},
 {key:'check',label:'检查管理',icon:'el-icon-monitor',selectedIcon:'el-icon-monitor',component:'Services',roles:['EXAM','DOCTOR']},
 {key:'lab',label:'检验管理',icon:'el-icon-files',selectedIcon:'el-icon-folder-opened',component:'Services',roles:['LAB','DOCTOR']},
 {key:'procedure',label:'处置管理',icon:'el-icon-first-aid-kit',selectedIcon:'el-icon-first-aid-kit',component:'Services',roles:['PROCEDURE','DOCTOR']},
 {key:'pharmacy',label:'药房管理',icon:'el-icon-box',selectedIcon:'el-icon-box',component:'Pharmacy',roles:['PHARMACIST']},
 {key:'billing',label:'收费退费',icon:'el-icon-wallet',selectedIcon:'el-icon-wallet',component:'Billing',roles:['REGISTRAR','DOCTOR']},
 {key:'audit',label:'操作日志',icon:'el-icon-notebook-2',selectedIcon:'el-icon-notebook-2',component:'Audit',roles:['ADMIN']}
 ]}},
 computed:{visibleItems(){return this.items.filter(i=>!i.roles.length||this.user.role==='ADMIN'||i.roles.includes(this.user.role))},currentItem(){return this.items.find(i=>i.key===this.page)||this.items[0]},pageTitle(){return this.currentItem.label},currentView(){return this.currentItem.component}},
 async created(){window.addEventListener('his-expired',this.expired);if(sessionStorage.getItem('his_token'))await this.restore();try{this.guestEnabled=(await api.get('/auth/guest')).enabled}catch{this.guestEnabled=false}if(!this.user&&this.guestEnabled&&new URLSearchParams(window.location.search).get('guest')==='1')await this.enterGuest()},beforeDestroy(){window.removeEventListener('his-expired',this.expired)},
 methods:{async enterGuest(){if(this.guestLoading||this.loading)return;this.guestLoading=true;try{const result=await api.post('/auth/guest');sessionStorage.setItem('his_token',result.token);this.meta=await api.get('/meta');this.user=result.user;this.ensurePage();const url=new URL(window.location.href);if(url.searchParams.has('guest')){url.searchParams.delete('guest');window.history.replaceState(null,'',url)}}catch(e){sessionStorage.removeItem('his_token');this.$message.error(e.message)}finally{this.guestLoading=false}},goHome(){this.page='dashboard';window.scrollTo({top:0,behavior:'auto'})},roleName(r){return{ADMIN:'系统管理员',REGISTRAR:'挂号收费员',DOCTOR:'门诊医生',EXAM:'检查科医生',LAB:'检验科医生',PROCEDURE:'处置科医生',PHARMACIST:'药房管理员'}[r]||r},expired(){this.user=null;this.$message.warning('登录已过期，请重新登录')},async restore(){try{this.user=await api.get('/auth/me');this.meta=await api.get('/meta');this.ensurePage()}catch{this.user=null}},ensurePage(){if(!this.visibleItems.some(x=>x.key===this.page))this.page='dashboard'},async login(){if(this.guestLoading||this.loading)return;if(!this.credentials.username||!this.credentials.password)return this.$message.warning('请输入用户名和密码');this.loading=true;try{const result=await api.post('/auth/login',this.credentials);sessionStorage.setItem('his_token',result.token);this.user=result.user;this.meta=await api.get('/meta');this.ensurePage();this.credentials.password='';this.$message.success('登录成功')}catch(e){this.$message.error(e.message)}finally{this.loading=false}},async accountCommand(command){if(command==='logout'){try{await api.post('/auth/logout')}catch{ /* Session may already be invalid. */ }sessionStorage.removeItem('his_token');this.user=null;this.page='dashboard'}}}
}
</script>

