import { createRouter, createWebHistory } from 'vue-router'
import TrainingView from '../views/TrainingView.vue'
import InferenceView from '../views/InferenceView.vue'
import SettingsView from '../views/SettingsView.vue'
import LabelView from '../views/LabelView.vue'

const routes = [
  {
    path: '/',
    redirect: '/training'
  },
  {
    path: '/training',
    name: 'Training',
    component: TrainingView
  },
  {
    path: '/inference',
    name: 'Inference',
    component: InferenceView
  },
  {
    path: '/label',
    name: 'Label',
    component: LabelView
  },
  {
    path: '/settings',
    name: 'Settings',
    component: SettingsView
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router