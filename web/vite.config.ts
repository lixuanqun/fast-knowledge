/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'
import { resolve } from 'path'

export default defineConfig({
  // 生产单 JAR 部署时前端托管于 context-path(/api/v1) 之下：
  // VITE_ROUTER_BASE=/api/v1/ npm run build —— 资源 base 与 vue-router base 由同一变量驱动
  base: process.env.VITE_ROUTER_BASE || '/',
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      dts: 'src/components.d.ts'
    }),
    viteCompression({
      algorithm: 'gzip',
      threshold: 1024,
      deleteOriginFile: false
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/styles/design-tokens.scss" as *;\n`
      }
    }
  },
  server: {
    port: 5174,
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8088',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return

          if (id.includes('@tanstack')) {
            return 'tanstack-query'
          }
          if (id.includes('marked') || id.includes('dompurify')) {
            return 'markdown'
          }
          if (
            id.includes('/vue/') ||
            id.includes('/vue-router/') ||
            id.includes('/pinia/') ||
            id.includes('@vue/')
          ) {
            return 'vue-vendor'
          }
        }
      }
    },
    chunkSizeWarningLimit: 600
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}']
  }
})
