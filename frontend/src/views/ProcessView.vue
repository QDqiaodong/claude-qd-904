<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>制茶工序流转</h2>
      <span class="tf-sub">按茶类工序序列推进 · 不能跳步 · 工序参数不达标不允许结束</span>
    </div>

    <!-- ============ 批次选择 + 开单 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        加工批次
        <el-button link type="primary" size="small" @click="showCreate = !showCreate">
          {{ showCreate ? '收起' : '新建制茶工单' }}
        </el-button>
      </div>

      <el-form size="small" inline>
        <el-form-item label="批次">
          <el-select v-model="batchNo" style="width: 300px" @change="loadDetail">
            <el-option
              v-for="o in orders"
              :key="o.batchNo"
              :label="o.batchNo + '（' + o.teaTypeName + ' ' + o.inputQtyKg + 'kg）'"
              :value="o.batchNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="loadAll">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-form v-if="showCreate" size="small" inline class="pc-create">
        <el-form-item label="茶类">
          <el-select v-model="createForm.teaType" style="width: 120px">
            <el-option v-for="t in teaTypeList" :key="t" :label="teaName(t)" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="投料 kg">
          <el-input-number v-model="createForm.inputQtyKg" :min="0.01" :max="9999" :step="10" :precision="2" style="width: 130px" />
        </el-form-item>
        <el-form-item label="来源槽">
          <el-select v-model="createForm.sourceTroughNo" clearable style="width: 140px">
            <el-option v-for="t in troughs" :key="t.troughNo" :label="t.troughNo" :value="t.troughNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="制茶师傅">
          <el-input v-model="createForm.operator" style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="createOrder">开单</el-button>
        </el-form-item>
        <div class="pc-create-seq">工序序列：{{ flowPreview }}</div>
      </el-form>

      <div v-if="detail" class="pc-meta">
        <span>茶类 <b>{{ detail.teaTypeName }}</b></span>
        <span>工序序列 <b>{{ detail.flowNames.join(' → ') }}</b></span>
        <span>投料 <b>{{ num(detail.inputQtyKg) }} kg</b></span>
        <span>产出 <b>{{ num(detail.outputQtyKg) }} kg</b></span>
        <span>来源槽 <b>{{ detail.sourceTroughNo || '-' }}</b></span>
        <span>师傅 <b>{{ detail.operator }}</b></span>
        <span>状态 <b>{{ detail.status === 'DONE' ? '已完成' : '在制' }}</b></span>
      </div>
    </section>

    <!-- ============ 横向工序链 ============ -->
    <section v-if="detail" class="tf-card">
      <div class="tf-card-title">
        工序链
        <small>当前：{{ detail.currentStepName }}</small>
      </div>
      <div class="pc-chain">
        <template v-for="(name, i) in detail.flowNames" :key="i">
          <div
            class="pc-node"
            :class="{ 'is-done': i < curIndex, 'is-current': i === curIndex, 'is-future': i > curIndex, 'is-sel': i === selIndex }"
            @click="selIndex = i"
          >
            <div class="pc-node-dot">
              <span v-if="i < curIndex">✓</span>
              <span v-else-if="i === curIndex">{{ runningOf(detail.flow[i]) ? '●' : i + 1 }}</span>
              <span v-else>{{ i + 1 }}</span>
            </div>
            <div class="pc-node-name">{{ name }}</div>
            <div class="pc-node-state">
              {{ i < curIndex ? '已完成' : i === curIndex ? (runningOf(detail.flow[i]) ? '执行中' : '待开工') : '未开始' }}
            </div>
          </div>
          <div v-if="i < detail.flowNames.length - 1" class="pc-arrow">→</div>
        </template>
      </div>
    </section>

    <!-- ============ 工序参数面板 ============ -->
    <section v-if="detail" class="tf-card pc-panel">
      <div class="tf-card-title">
        {{ detail.flowNames[selIndex] }} · 参数面板
        <small>{{ panelHint }}</small>
      </div>

      <div class="pc-panel-body">
        <el-form label-width="104px" size="small" class="pc-params">
          <el-form-item label="使用设备">
            <el-select v-model="form.equipmentCode" clearable filterable style="width: 240px">
              <el-option
                v-for="e in stepEquipments"
                :key="e.equipCode"
                :label="e.equipCode + ' ' + e.equipName + (e.status === 'FAULT' ? '（故障）' : '')"
                :value="e.equipCode"
                :disabled="e.status === 'FAULT'"
              />
            </el-select>
          </el-form-item>

          <template v-for="f in fields" :key="f.key">
            <el-form-item :label="f.label + (f.unit ? ' ' + f.unit : '')">
              <el-input-number
                v-model="form[f.key]"
                :min="f.min"
                :max="f.max"
                :step="f.step"
                :precision="f.precision"
                style="width: 200px"
              />
              <span v-if="f.tip" class="pc-tip">{{ f.tip }}</span>
            </el-form-item>
          </template>

          <el-form-item label="参数速记">
            <el-input v-model="form.paramProfile" placeholder="如：滚筒温度 235℃，投叶 180kg" style="width: 320px" />
          </el-form-item>
          <el-form-item label="操作人">
            <el-input v-model="form.operator" style="width: 200px" />
          </el-form-item>
          <el-form-item label="异常记录">
            <el-input v-model="form.abnormalNote" placeholder="无异常可留空" style="width: 320px" />
          </el-form-item>
        </el-form>

        <div class="pc-actions">
          <el-button type="primary" size="large" :loading="starting" :disabled="!canStart" @click="startStep">
            开始本工序
          </el-button>
          <el-button type="success" size="large" :loading="finishing" :disabled="!canFinish" @click="finishStep">
            结束本工序
          </el-button>
          <div class="pc-hint" :class="{ 'pc-hint-bad': !canStart && !canFinish }">{{ panelHint }}</div>
        </div>
      </div>
    </section>

    <!-- ============ 工序执行记录 ============ -->
    <section v-if="detail" class="tf-card">
      <div class="tf-card-title">
        工序执行记录
        <small>批次 {{ detail.batchNo }} · 共 {{ steps.length }} 条</small>
      </div>
      <el-table :data="steps" size="small" border stripe empty-text="该批次还没有工序记录">
        <el-table-column label="序" width="50" align="center">
          <template #default="{ row }">{{ row.stepIndex + 1 }}</template>
        </el-table-column>
        <el-table-column label="工序" width="90">
          <template #default="{ row }">{{ stepName(row) }}</template>
        </el-table-column>
        <el-table-column prop="equipmentCode" label="设备" width="100" />
        <el-table-column label="开始" width="130">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束" width="130">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="温度 ℃" width="90" align="right">
          <template #default="{ row }">{{ row.paramTempC == null ? '-' : row.paramTempC }}</template>
        </el-table-column>
        <el-table-column label="二段 ℃" width="80" align="right">
          <template #default="{ row }">{{ row.paramTemp2C == null ? '-' : row.paramTemp2C }}</template>
        </el-table-column>
        <el-table-column label="时长 min" width="90" align="right">
          <template #default="{ row }">{{ row.paramMinutes == null ? '-' : row.paramMinutes }}</template>
        </el-table-column>
        <el-table-column label="转速" width="80" align="right">
          <template #default="{ row }">{{ row.paramRpm == null ? '-' : row.paramRpm }}</template>
        </el-table-column>
        <el-table-column label="压力 kg" width="80" align="right">
          <template #default="{ row }">{{ row.paramPressure == null ? '-' : row.paramPressure }}</template>
        </el-table-column>
        <el-table-column label="投叶 kg" width="90" align="right">
          <template #default="{ row }">{{ row.paramLeafQty == null ? '-' : row.paramLeafQty }}</template>
        </el-table-column>
        <el-table-column label="湿度 %" width="80" align="right">
          <template #default="{ row }">{{ row.humidityPct == null ? '-' : row.humidityPct }}</template>
        </el-table-column>
        <el-table-column prop="paramProfile" label="参数速记" min-width="150" />
        <el-table-column prop="abnormalNote" label="异常" min-width="110" />
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'RUNNING' ? 'warning' : 'success'">
              {{ row.status === 'RUNNING' ? '执行中' : '已完成' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <div v-if="!detail" class="tf-empty">请选择或新建加工批次</div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { equipmentApi, processApi, witheringApi } from '../api'

const TEA_TYPES = ['GREEN', 'RED', 'OOLONG', 'WHITE', 'PUER']
const TEA_NAMES = { GREEN: '绿茶', RED: '红茶', OOLONG: '乌龙茶', WHITE: '白茶', PUER: '普洱茶' }
const STEP_NAMES = { SPREAD: '摊青', MAKING: '做青', FIX: '杀青', ROLL: '揉捻', FERMENT: '发酵', DRY: '干燥', PICK: '拣剔' }
const ALIAS = {
  RED: { FERMENT: '发酵', SPREAD: '萎凋' },
  PUER: { FERMENT: '渥堆', SPREAD: '摊青' },
  OOLONG: { SPREAD: '晒青' },
  WHITE: { SPREAD: '摊青（自然萎凋）' }
}
/* 各工序参数字段及默认值 */
const STEP_FIELDS = {
  SPREAD: ['paramMinutes', 'humidityPct', 'paramLeafQty'],
  MAKING: ['paramMinutes', 'paramRpm'],
  FIX: ['paramTempC', 'paramMinutes', 'paramLeafQty'],
  ROLL: ['paramRpm', 'paramPressure', 'paramMinutes'],
  FERMENT: ['paramTempC', 'humidityPct', 'paramMinutes'],
  DRY: ['paramTempC', 'paramMinutes', 'paramTemp2C', 'paramMinutes2'],
  PICK: ['paramMinutes', 'paramLeafQty']
}
const FIELD_META = {
  paramTempC: { key: 'paramTempC', label: '温度', unit: '℃', min: 0, max: 400, step: 5, precision: 1 },
  paramTemp2C: { key: 'paramTemp2C', label: '足火温度', unit: '℃', min: 0, max: 400, step: 5, precision: 1 },
  paramMinutes: { key: 'paramMinutes', label: '时长', unit: 'min', min: 0, max: 100000, step: 5, precision: 0 },
  paramMinutes2: { key: 'paramMinutes2', label: '足火时长', unit: 'min', min: 0, max: 100000, step: 5, precision: 0 },
  paramRpm: { key: 'paramRpm', label: '转速', unit: 'r/min', min: 0, max: 200, step: 5, precision: 0 },
  paramPressure: { key: 'paramPressure', label: '压力', unit: 'kg', min: 0, max: 100, step: 0.5, precision: 1 },
  paramLeafQty: { key: 'paramLeafQty', label: '投叶量', unit: 'kg', min: 0, max: 99999, step: 10, precision: 2 },
  humidityPct: { key: 'humidityPct', label: '相对湿度', unit: '%', min: 0, max: 100, step: 1, precision: 1 }
}
const MIN_FIX_TEMP = { GREEN: 220, OOLONG: 200, PUER: 180 }
const MIN_FERMENT_MIN = { RED: 180, PUER: 720, OOLONG: 120 }
const EQ_OF_STEP = { FIX: '杀青机', ROLL: '揉捻机', FERMENT: '发酵室', DRY: '烘干机', PICK: '提香机' }

const teaTypeList = TEA_TYPES
const props = defineProps({
  teaType: { type: String, default: 'GREEN' },
  step: { type: String, default: '' }
})

const orders = ref([])
const troughs = ref([])
const equipments = ref([])
const detail = ref(null)
const steps = ref([])
const batchNo = ref('')
const selIndex = ref(0)
const showCreate = ref(false)
const creating = ref(false)
const starting = ref(false)
const finishing = ref(false)

const createForm = reactive({ teaType: props.teaType || 'GREEN', inputQtyKg: 180, sourceTroughNo: '', operator: '' })

const form = reactive({
  equipmentCode: '',
  paramTempC: null,
  paramTemp2C: null,
  paramMinutes: null,
  paramMinutes2: null,
  paramRpm: null,
  paramPressure: null,
  paramLeafQty: null,
  humidityPct: null,
  paramProfile: '',
  operator: '',
  abnormalNote: ''
})

const teaName = (t) => TEA_NAMES[t] || t || '-'
const num = (v, d = 2) => (v === null || v === undefined ? '-' : Number(v).toFixed(d))
const fmtTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '-')

function stepName(row) {
  const tea = row.teaType || (detail.value && detail.value.teaType)
  const alias = ALIAS[tea] || {}
  return alias[row.stepName] || STEP_NAMES[row.stepName] || row.stepName
}

const curIndex = computed(() => {
  const d = detail.value
  if (!d) return 0
  return typeof d.currentStepIndex === 'number' ? d.currentStepIndex : 0
})
const selStep = computed(() => (detail.value && detail.value.flow[selIndex.value]) || '')
const selStepName = computed(() => (detail.value && detail.value.flowNames[selIndex.value]) || '')

const fields = computed(() => {
  const keys = STEP_FIELDS[selStep.value] || []
  const out = keys.map((k) => ({ ...FIELD_META[k] }))
  const tea = detail.value ? detail.value.teaType : 'GREEN'
  out.forEach((f) => {
    if (f.key === 'paramTempC' && selStep.value === 'FIX' && MIN_FIX_TEMP[tea]) {
      f.tip = `${teaName(tea)}杀青不得低于 ${MIN_FIX_TEMP[tea]}℃`
    }
    if (f.key === 'paramMinutes' && selStep.value === 'FERMENT' && MIN_FERMENT_MIN[tea]) {
      f.tip = `不少于 ${MIN_FERMENT_MIN[tea]} 分钟`
    }
  })
  return out
})

const stepEquipments = computed(() => {
  const type = EQ_OF_STEP[selStep.value]
  if (!type) return equipments.value
  return equipments.value.filter((e) => e.equipType === type)
})

const TEA_FLOW = {
  GREEN: ['SPREAD', 'FIX', 'ROLL', 'DRY', 'PICK'],
  RED: ['SPREAD', 'ROLL', 'FERMENT', 'DRY', 'PICK'],
  OOLONG: ['SPREAD', 'MAKING', 'FIX', 'ROLL', 'DRY', 'PICK'],
  WHITE: ['SPREAD', 'DRY', 'PICK'],
  PUER: ['SPREAD', 'FIX', 'ROLL', 'FERMENT', 'DRY', 'PICK']
}

const flowPreview = computed(() =>
  (TEA_FLOW[createForm.teaType] || []).map((s) => ALIAS[createForm.teaType]?.[s] || STEP_NAMES[s]).join(' → ')
)
function runningOf(step) {
  return steps.value.find((s) => s.stepName === step && s.status === 'RUNNING') || null
}

const panelHint = computed(() => {
  const d = detail.value
  if (!d) return ''
  if (d.status === 'DONE') return '本批次已完成全部工序'
  if (selIndex.value > curIndex.value) return `工序不能跳步，当前应先做「${d.flowNames[curIndex.value]}」`
  if (selIndex.value < curIndex.value) return `「${selStepName.value}」已完成，当前工序是「${d.flowNames[curIndex.value]}」`
  return runningOf(selStep.value) ? `「${selStepName.value}」执行中，记录完参数后点「结束本工序」` : `可以开始「${selStepName.value}」`
})

const canStart = computed(() => {
  const d = detail.value
  if (!d || d.status === 'DONE') return false
  if (selIndex.value !== curIndex.value) return false
  return !runningOf(selStep.value)
})

const canFinish = computed(() => {
  const d = detail.value
  if (!d || d.status === 'DONE') return false
  if (selIndex.value !== curIndex.value) return false
  return !!runningOf(selStep.value)
})

function fillForm(step) {
  form.equipmentCode = ''
  form.paramTempC = null
  form.paramTemp2C = null
  form.paramMinutes = null
  form.paramMinutes2 = null
  form.paramRpm = null
  form.paramPressure = null
  form.paramLeafQty = null
  form.humidityPct = null
  form.paramProfile = ''
  form.operator = (detail.value && detail.value.operator) || ''
  form.abnormalNote = ''

  const tea = detail.value ? detail.value.teaType : 'GREEN'
  const preset = {
    FIX: { paramTempC: Math.max(MIN_FIX_TEMP[tea] || 200, 220), paramMinutes: 4, paramLeafQty: Number(detail.value?.inputQtyKg || 180) },
    ROLL: { paramRpm: 45, paramPressure: 8.5, paramMinutes: 35 },
    FERMENT: { paramTempC: tea === 'PUER' ? 45 : 28, humidityPct: tea === 'PUER' ? 72 : 92, paramMinutes: MIN_FERMENT_MIN[tea] || 180 },
    DRY: { paramTempC: 110, paramMinutes: 25, paramTemp2C: 90, paramMinutes2: 40 },
    SPREAD: { paramMinutes: 480, humidityPct: 68, paramLeafQty: Number(detail.value?.inputQtyKg || 180) },
    MAKING: { paramMinutes: 90, paramRpm: 18 },
    PICK: { paramMinutes: 60, paramLeafQty: Number(detail.value?.outputQtyKg || 40) }
  }[step]
  Object.assign(form, preset || {})

  const rec = steps.value.find((s) => s.stepName === step)
  if (rec) {
    if (rec.equipmentCode) form.equipmentCode = rec.equipmentCode
    if (rec.paramTempC != null) form.paramTempC = Number(rec.paramTempC)
    if (rec.paramTemp2C != null) form.paramTemp2C = Number(rec.paramTemp2C)
    if (rec.paramMinutes != null) form.paramMinutes = Number(rec.paramMinutes)
    if (rec.paramMinutes2 != null) form.paramMinutes2 = Number(rec.paramMinutes2)
    if (rec.paramRpm != null) form.paramRpm = Number(rec.paramRpm)
    if (rec.paramPressure != null) form.paramPressure = Number(rec.paramPressure)
    if (rec.paramLeafQty != null) form.paramLeafQty = Number(rec.paramLeafQty)
    if (rec.humidityPct != null) form.humidityPct = Number(rec.humidityPct)
    if (rec.paramProfile) form.paramProfile = rec.paramProfile
    if (rec.operator) form.operator = rec.operator
    if (rec.abnormalNote) form.abnormalNote = rec.abnormalNote
  }
}

function payload(step) {
  return {
    stepName: step,
    equipmentCode: form.equipmentCode || undefined,
    paramTempC: form.paramTempC == null ? undefined : form.paramTempC,
    paramTemp2C: form.paramTemp2C == null ? undefined : form.paramTemp2C,
    paramMinutes: form.paramMinutes == null ? undefined : form.paramMinutes,
    paramMinutes2: form.paramMinutes2 == null ? undefined : form.paramMinutes2,
    paramRpm: form.paramRpm == null ? undefined : form.paramRpm,
    paramPressure: form.paramPressure == null ? undefined : form.paramPressure,
    paramLeafQty: form.paramLeafQty == null ? undefined : form.paramLeafQty,
    humidityPct: form.humidityPct == null ? undefined : form.humidityPct,
    paramProfile: form.paramProfile || undefined,
    operator: form.operator || undefined,
    abnormalNote: form.abnormalNote || undefined
  }
}

async function loadAll() {
  try {
    const res = await processApi.list()
    orders.value = res && res.data ? res.data : []
    if (!orders.value.length) {
      detail.value = null
      return
    }
    if (!batchNo.value || !orders.value.some((o) => o.batchNo === batchNo.value)) {
      batchNo.value = orders.value[0].batchNo
    }
    await loadDetail()
  } catch (e) {
    ElMessage.error('加载批次失败：' + e.message)
  }
}

async function loadDetail() {
  if (!batchNo.value) return
  try {
    const res = await processApi.detail(batchNo.value)
    detail.value = res && res.data ? res.data : null
    const sres = await processApi.steps(batchNo.value)
    steps.value = sres && sres.data ? sres.data : []
    if (detail.value) {
      if (selIndex.value >= detail.value.flow.length) selIndex.value = 0
      const idx = props.step ? detail.value.flow.indexOf(String(props.step).toUpperCase()) : -1
      if (idx >= 0) selIndex.value = idx
      fillForm(detail.value.flow[selIndex.value])
    }
  } catch (e) {
    ElMessage.error(e.message || '加载批次详情失败')
  }
}

async function createOrder() {
  creating.value = true
  try {
    const res = await processApi.create({
      teaType: createForm.teaType,
      inputQtyKg: createForm.inputQtyKg,
      sourceTroughNo: createForm.sourceTroughNo || undefined,
      operator: createForm.operator || undefined
    })
    ElMessage.success(res && res.message ? res.message : '已开单')
    showCreate.value = false
    batchNo.value = res && res.data ? res.data.batchNo : ''
    await loadAll()
  } catch (e) {
    ElMessage.error(e.message || '开单失败')
  } finally {
    creating.value = false
  }
}

async function startStep() {
  if (!detail.value) return
  starting.value = true
  try {
    const res = await processApi.start(detail.value.batchNo, payload(selStep.value))
    ElMessage.success(res && res.message ? res.message : '工序已开始')
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || '开工失败')
  } finally {
    starting.value = false
  }
}

async function finishStep() {
  if (!detail.value) return
  finishing.value = true
  try {
    const res = await processApi.finish(detail.value.batchNo, payload(selStep.value))
    ElMessage.success(res && res.message ? res.message : '本工序完成')
    const d = res && res.data ? res.data : null
    await loadDetail()
    /* 结束后自动把面板切到新的当前工序 */
    if (d && typeof d.currentStepIndex === 'number' && d.flow && d.currentStepIndex < d.flow.length) {
      selIndex.value = d.currentStepIndex
      fillForm(d.flow[d.currentStepIndex])
    }
  } catch (e) {
    ElMessage.error(e.message || '结束工序失败')
  } finally {
    finishing.value = false
  }
}

async function loadSide() {
  try {
    const [e, t] = await Promise.all([equipmentApi.list(), witheringApi.ready()])
    equipments.value = e && e.data ? e.data : []
    troughs.value = t && t.data ? t.data : []
  } catch (err) {
    /* 设备/槽位列表失败不影响主流程 */
  }
}

watch(selIndex, (i) => {
  if (detail.value && detail.value.flow[i]) fillForm(detail.value.flow[i])
})

watch(
  () => props.step,
  (v) => {
    if (!detail.value || !v) return
    const idx = detail.value.flow.indexOf(String(v).toUpperCase())
    if (idx >= 0 && idx !== selIndex.value) selIndex.value = idx
  }
)

onMounted(() => {
  loadAll()
  loadSide()
})
</script>

<style scoped>
.pc-create {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.pc-create-seq {
  font-size: 12px;
  color: #5d6353;
  padding-bottom: 18px;
}

.pc-meta {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #5d6353;
  border-top: 1px dashed #c4b99f;
  padding-top: 8px;
}

.pc-meta b {
  color: #33691e;
}

/* ---- 工序链 ---- */
.pc-chain {
  display: flex;
  align-items: stretch;
  gap: 4px;
  overflow-x: auto;
  padding: 8px 2px 12px;
}

.pc-node {
  flex: 0 0 92px;
  text-align: center;
  border: 1px solid #ddd5c2;
  border-radius: 6px;
  padding: 10px 4px 8px;
  background: #fff;
  cursor: pointer;
  transition: all 0.25s ease;
}

.pc-node:hover {
  border-color: #5b8b3f;
}

.pc-node.is-done {
  background: #e7efdd;
  border-color: #a6c088;
}

.pc-node.is-current {
  background: #33691e;
  border-color: #33691e;
  transform: scale(1.14);
  box-shadow: 0 4px 14px rgba(51, 105, 30, 0.28);
  z-index: 2;
}

.pc-node.is-future {
  color: #9a9788;
  background: #f4efe4;
}

.pc-node.is-sel {
  outline: 2px dashed #b45309;
  outline-offset: 2px;
}

.pc-node-dot {
  width: 26px;
  height: 26px;
  line-height: 24px;
  margin: 0 auto 6px;
  border-radius: 50%;
  border: 1px solid currentColor;
  font-size: 12px;
  font-weight: 700;
}

.pc-node.is-current .pc-node-dot {
  color: #fff;
  border-color: #fff;
}

.pc-node.is-done .pc-node-dot {
  color: #33691e;
}

.pc-node-name {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
}

.pc-node.is-current .pc-node-name {
  color: #fff;
}

.pc-node-state {
  margin-top: 3px;
  font-size: 10px;
  opacity: 0.75;
}

.pc-arrow {
  align-self: center;
  color: #c4b99f;
  font-size: 16px;
}

/* ---- 参数面板 ---- */
.pc-panel-body {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.pc-params {
  flex: 1 1 460px;
  min-width: 340px;
}

.pc-tip {
  margin-left: 10px;
  font-size: 11px;
  color: #b45309;
}

.pc-actions {
  flex: 0 0 240px;
  border-left: 1px dashed #c4b99f;
  padding-left: 18px;
}

.pc-actions .el-button {
  width: 100%;
  margin: 0 0 10px 0;
}

.pc-hint {
  font-size: 12px;
  color: #5d6353;
  line-height: 1.7;
}

.pc-hint-bad {
  color: #b45309;
}
</style>
