<template>
  <div class="page-container dashboard-page">
    <AdminDashboard v-if="isAdmin" />
    <UserDashboard v-else />
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import AdminDashboard from './AdminDashboard.vue'
import UserDashboard from './UserDashboard.vue'

const { isAdmin } = storeToRefs(useAuthStore())
</script>
