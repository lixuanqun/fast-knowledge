<template>
  <svg class="trend-chart" viewBox="0 0 520 200" aria-hidden="true">
    <defs>
      <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#409eff" stop-opacity="0.25" />
        <stop offset="100%" stop-color="#409eff" stop-opacity="0.02" />
      </linearGradient>
    </defs>
    <polyline :points="areaPoints" fill="url(#trendFill)" stroke="none" />
    <polyline :points="linePoints" fill="none" stroke="#409eff" stroke-width="2.5" />
    <g v-for="(p, i) in points" :key="i">
      <circle :cx="p.x" :cy="p.y" r="4" fill="var(--fk-card-bg)" stroke="#409eff" stroke-width="2" />
    </g>
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 7 个数据点值 */
  values: number[]
}>()

const XS = [40, 120, 200, 280, 360, 440, 500]

const points = computed(() => {
  const max = Math.max(...props.values, 1)
  return props.values.map((v, i) => ({
    x: XS[i] ?? 500,
    y: 170 - (v / max) * 130
  }))
})

const linePoints = computed(() => points.value.map(p => `${p.x},${p.y}`).join(' '))

const areaPoints = computed(() => {
  const pts = points.value
  if (!pts.length) return ''
  return `${pts[0].x},180 ${linePoints.value} ${pts[pts.length - 1].x},180`
})
</script>

<style scoped lang="scss">
.trend-chart {
  width: 100%;
  height: 200px;
}
</style>