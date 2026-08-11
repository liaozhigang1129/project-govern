import { ref, onMounted, onBeforeUnmount } from 'vue'

/**
 * 移动端检测 composable (P2 #30).
 * 监听 window.resize, < 768px 返回 true.
 */
export function useIsMobile(breakpoint = 768) {
  const isMobile = ref(window.innerWidth < breakpoint)

  function onResize() {
    isMobile.value = window.innerWidth < breakpoint
  }

  onMounted(() => window.addEventListener('resize', onResize))
  onBeforeUnmount(() => window.removeEventListener('resize', onResize))

  return isMobile
}
