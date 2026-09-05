<template>
  <div class="streaming-hint">
    <span class="streaming-dots"><i /><i /><i /></span>
    {{ text || '正在生成...' }}
  </div>
</template>

<script setup lang="ts">
defineProps<{ text?: string }>()
</script>

<style scoped lang="scss">
.streaming-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $fk-text-secondary;
  font-size: 14px;
}

.streaming-dots {
  display: inline-flex;
  gap: 4px;
}

.streaming-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $fk-primary;
  animation: dot-bounce 1.2s infinite ease-in-out;
}

.streaming-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.streaming-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes dot-bounce {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>