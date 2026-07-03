<template>
  <svg
    class="empty-illus"
    :width="size"
    :height="size"
    viewBox="0 0 120 120"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <template v-if="variant === 'kbs'">
      <rect x="30" y="48" width="60" height="44" rx="6" class="illus-fill" />
      <path d="M30 54h60" class="illus-stroke" />
      <path d="M72 28 L88 40 L72 52 Z" fill="var(--fk-primary)" opacity="0.7" />
      <path d="M76 36 L84 42" stroke="var(--fk-chat-user-text)" stroke-width="2" stroke-linecap="round" />
    </template>
    <template v-else-if="variant === 'docs'">
      <rect x="36" y="32" width="48" height="56" rx="4" class="illus-fill" />
      <path d="M44 44h32M44 54h24M44 64h28" class="illus-stroke" stroke-width="2" stroke-linecap="round" />
      <circle cx="78" cy="78" r="16" fill="var(--fk-primary)" />
      <path d="M78 72v8M74 76h8" stroke="var(--fk-chat-user-text)" stroke-width="2" stroke-linecap="round" />
    </template>
    <template v-else-if="variant === 'search'">
      <circle cx="52" cy="52" r="24" stroke="var(--fk-primary)" stroke-width="6" opacity="0.35" />
      <path d="M68 68 L84 84" stroke="var(--fk-primary)" stroke-width="6" stroke-linecap="round" />
    </template>
    <template v-else-if="variant === 'qa'">
      <rect x="28" y="36" width="64" height="48" rx="10" class="illus-fill" />
      <circle cx="44" cy="56" r="4" fill="var(--fk-primary)" />
      <circle cx="60" cy="56" r="4" fill="var(--fk-primary)" />
      <circle cx="76" cy="56" r="4" fill="var(--fk-primary)" />
    </template>
    <template v-else-if="variant === 'qa-result'">
      <rect x="32" y="34" width="56" height="52" rx="6" class="illus-muted-fill" />
      <path d="M40 48h40M40 58h32M40 68h36" class="illus-muted-stroke" stroke-width="2" stroke-linecap="round" />
    </template>
    <template v-else-if="variant === 'chat' || variant === 'chat-empty'">
      <rect x="30" y="38" width="60" height="44" rx="10" class="illus-fill" />
      <path d="M42 56h36M42 66h24" class="illus-stroke" stroke-width="2" stroke-linecap="round" />
    </template>
    <template v-else-if="variant === 'writer'">
      <rect x="34" y="30" width="52" height="60" rx="4" class="illus-fill" />
      <path d="M42 44h36M42 54h28M42 64h32" class="illus-stroke" stroke-width="2" stroke-linecap="round" />
      <path d="M70 74l16 12-8 4-4 8z" fill="var(--fk-primary)" opacity="0.8" />
    </template>
    <template v-else-if="variant === 'members'">
      <circle cx="60" cy="46" r="14" class="illus-fill" stroke-width="2" />
      <path d="M36 88c4-16 44-16 48 0" class="illus-stroke illus-fill-path" stroke-width="2" />
    </template>
    <template v-else>
      <rect x="38" y="40" width="44" height="40" rx="8" class="illus-muted-fill" />
      <path d="M50 58h20" class="illus-muted-stroke" stroke-width="2" stroke-linecap="round" />
    </template>
  </svg>
</template>

<script setup lang="ts">
defineProps<{
  variant: string
  size?: number
}>()
</script>

<style scoped>
.empty-illus .illus-fill {
  fill: var(--fk-illus-fill);
  stroke: var(--fk-illus-stroke);
}

.empty-illus .illus-stroke {
  stroke: var(--fk-illus-stroke);
}

.empty-illus .illus-muted-fill {
  fill: var(--fk-illus-muted-fill);
  stroke: var(--fk-illus-muted-stroke);
}

.empty-illus .illus-muted-stroke {
  stroke: var(--fk-illus-muted-stroke);
}

.empty-illus .illus-fill-path {
  fill: var(--fk-illus-fill);
}

.empty-illus {
  display: block;
  margin: 0 auto;
}
</style>
