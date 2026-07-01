<template>
  <el-dialog v-model="visible" title="修改密码" width="420px" destroy-on-close @closed="reset">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { changePassword } from '@/api'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const visible = ref(false)
const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '新密码至少 6 位', trigger: 'blur' }],
  confirmPassword: [{
    validator: (_r, v, cb) => {
      if (v !== form.newPassword) cb(new Error('两次密码不一致'))
      else cb()
    },
    trigger: 'blur'
  }]
}

function open() {
  visible.value = true
}

function reset() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码已修改')
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '修改失败')
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
