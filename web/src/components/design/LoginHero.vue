<template>
  <div class="login-hero" aria-hidden="true">
    <div class="login-hero__circle login-hero__circle--tl" />
    <div class="login-hero__circle login-hero__circle--br" />
    <div class="login-hero__dots" />
    <svg
      v-if="!isDark"
      class="login-hero__illus"
      viewBox="0 0 320 280"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <rect x="48" y="72" width="120" height="148" rx="12" fill="#fff" fill-opacity="0.9" />
      <rect x="64" y="96" width="88" height="8" rx="4" fill="#d9ecff" />
      <rect x="64" y="116" width="72" height="6" rx="3" fill="#e8f3ff" />
      <rect x="64" y="132" width="80" height="6" rx="3" fill="#e8f3ff" />
      <rect x="88" y="40" width="104" height="128" rx="12" fill="#fff" stroke="#b3d8ff" stroke-width="2" />
      <rect x="104" y="64" width="72" height="6" rx="3" fill="#d9ecff" />
      <rect x="104" y="80" width="56" height="6" rx="3" fill="#e8f3ff" />
      <rect x="104" y="96" width="64" height="6" rx="3" fill="#e8f3ff" />
      <circle cx="228" cy="168" r="52" fill="#409eff" fill-opacity="0.12" />
      <circle cx="228" cy="168" r="36" fill="#409eff" fill-opacity="0.25" />
      <path
        d="M228 148l14 10v24c0 4-3 7-7 7h-14c-4 0-7-3-7-7v-24l14-10z"
        fill="#409eff"
        fill-opacity="0.85"
      />
      <path
        d="M220 176l6 6 12-14"
        stroke="#fff"
        stroke-width="3"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const isDark = ref(false)
let observer: MutationObserver | undefined

function readTheme() {
  isDark.value = document.documentElement.getAttribute('data-theme') === 'dark'
}

onMounted(() => {
  readTheme()
  observer = new MutationObserver(readTheme)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class'] })
})

onUnmounted(() => {
  observer?.disconnect()
})
</script>

<style scoped lang="scss">
.login-hero {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.login-hero__circle {
  position: absolute;
  border-radius: 50%;
  background: var(--fk-hero-accent);
}

.login-hero__circle--tl {
  width: 280px;
  height: 280px;
  top: -80px;
  left: -60px;
}

.login-hero__circle--br {
  width: 360px;
  height: 360px;
  right: -100px;
  bottom: -120px;
}

.login-hero__dots {
  position: absolute;
  left: 8%;
  top: 28%;
  width: 140px;
  height: 140px;
  background-image: radial-gradient($fk-primary 1.5px, transparent 1.5px);
  background-size: 14px 14px;
  opacity: 0.2;
}

.login-hero__illus {
  position: absolute;
  right: 12%;
  top: 50%;
  width: min(320px, 28vw);
  transform: translateY(-50%);
  opacity: 0.95;
}

@media (max-width: 960px) {
  .login-hero__illus {
    opacity: 0.35;
    right: 4%;
  }
}
</style>
