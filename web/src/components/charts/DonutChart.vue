<template>
  <div class="donut-wrap">
    <svg class="donut-chart" viewBox="0 0 120 120" aria-hidden="true">
      <circle
        v-for="(seg, i) in segments"
        :key="i"
        cx="60"
        cy="60"
        r="42"
        fill="none"
        :stroke="seg.color"
        stroke-width="16"
        :stroke-dasharray="`${seg.len} ${CIRCUMFERENCE - seg.len}`"
        :stroke-dashoffset="seg.offset"
        transform="rotate(-90 60 60)"
      />
    </svg>
    <div class="donut-center">
      <strong>{{ centerValue }}</strong>
      <span>{{ centerLabel }}</span>
    </div>
    <ul class="donut-legend">
      <li v-for="item in items" :key="item.label">
        <i :style="{ background: item.color }" />
        {{ item.label }} {{ item.count }} ({{ item.percent }}%)
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface DonutItem {
  label: string
  count: number
  percent: number
  color: string
}

const props = defineProps<{
  items: DonutItem[]
  centerValue: number | string
  centerLabel: string
}>()

const CIRCUMFERENCE = 2 * Math.PI * 42

const segments = computed(() => {
  let offset = 0
  return props.items.map(item => {
    const len = (item.percent / 100) * CIRCUMFERENCE
    const seg = { len, offset: -offset, color: item.color }
    offset += len
    return seg
  })
})
</script>

<style scoped lang="scss">
.donut-wrap {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  position: relative;
}

.donut-chart {
  width: 120px;
  height: 120px;
  flex-shrink: 0;
}

.donut-center {
  position: absolute;
  left: 60px;
  top: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}

.donut-center strong {
  display: block;
  font-size: 16px;
  color: $fk-text-primary;
}

.donut-center span {
  font-size: 11px;
  color: $fk-text-secondary;
}

.donut-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 13px;
  color: $fk-text-regular;
  flex: 1;
  min-width: 160px;
}

.donut-legend li {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.donut-legend i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
</style>