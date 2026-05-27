import { createRouter, createWebHistory } from 'vue-router'

const routes = [{
        path: '/',
        name: 'Home',
        component: () =>
            import ('../views/Home.vue'),
        meta: { title: '首页' }
    },
    {
        path: '/upload',
        name: 'Upload',
        component: () =>
            import ('../views/Upload.vue'),
        meta: { title: '数据上传' }
    },
    {
        path: '/rules',
        name: 'Rules',
        component: () =>
            import ('../views/Rules.vue'),
        meta: { title: '规则配置' }
    },
    {
        path: '/validation',
        name: 'Validation',
        component: () =>
            import ('../views/Validation.vue'),
        meta: { title: '数据校验' }
    },
    {
        path: '/findings',
        name: 'Findings',
        component: () =>
            import ('../views/Findings.vue'),
        meta: { title: '异常分析' }
    },
    {
        path: '/report',
        name: 'Report',
        component: () =>
            import ('../views/Report.vue'),
        meta: { title: '报告导出' }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    document.title = `${to.meta.title} - 业务数据准确性验证工具`
    next()
})

export default router