<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>设备清洗交接台</h2>
      <span class="tf-sub">
        <el-button link type="primary" size="small" @click="$router.push('/process')">← 返回工序台</el-button>
        ｜ 工序收尾自动出交接单 · 清洗人填残留风险/方式/起止时间 · 负责人确认恢复可用前，未开始、清洗中、待确认、故障一律不可开工
      </span>
    </div>

    <!-- ============ 说明条：冲突取舍口径 ============ -->
    <section class="tf-card hd-policy">
      <div>
        <b>清洗与报障冲突的取舍：</b>
        负责人确认恢复可用<b>之前</b>报障 → 设备<b>立即冻结</b>，本次清洗作废、转维修；
        确认<b>之后</b>报障 → <b>保留已确认的清洗结果</b>，设备转维修，复检通过后恢复使用。
        两种情况下，负责人确认前设备均不得接新批次。
      </div>
      <el-button text size="small" @click="loadAll">刷新状态</el-button>
    </section>

    <!-- ============ 设备列表 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        设备列表
        <small>开工可用性与工序台同口径，刷新后保持一致 · 无交接单的旧设备标注“旧设备”沿用原状态</small>
      </div>

      <div class="hd-filter">
        <el-radio-group v-model="equipFilter" size="small">
          <el-radio-button label="ALL">全部 {{ board.length }}</el-radio-button>
          <el-radio-button label="BLOCKED">不可开工 {{ blockedCount }}</el-radio-button>
          <el-radio-button label="AVAILABLE">可开工 {{ availableCount }}</el-radio-button>
        </el-radio-group>
        <el-select v-model="typeFilter" size="small" clearable placeholder="设备类型" style="width: 140px">
          <el-option v-for="t in equipTypes" :key="t" :label="t" :value="t" />
        </el-select>
      </div>

      <el-table :data="filteredBoard" size="small" border stripe :row-class-name="rowClass">
        <el-table-column prop="equipCode" label="编号" width="90" />
        <el-table-column prop="equipName" label="名称" min-width="150" />
        <el-table-column prop="equipType" label="类型" width="90" />
        <el-table-column label="原状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="rawStatusType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交接状态" width="128">
          <template #default="{ row }">
            <el-tag v-if="row.handoverStatus" size="small" :type="handoverTagType(row.handoverStatus)">
              {{ row.handoverStatusName }}
            </el-tag>
            <span v-else class="hd-legacy">旧设备 · 无交接单</span>
          </template>
        </el-table-column>
        <el-table-column label="当前交接单" width="170">
          <template #default="{ row }">
            <el-link v-if="row.activeHandoverNo" type="primary" :underline="false" @click="openByNo(row.activeHandoverNo)">
              {{ row.activeHandoverNo }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="能否接新批次" min-width="230">
          <template #default="{ row }">
            <el-tag v-if="row.available" size="small" type="success">✓ 可开工</el-tag>
            <div v-else class="hd-block">
              <el-tag size="small" type="danger">✕ 锁定</el-tag>
              <span class="hd-block-reason">{{ row.blockedReason }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row }">
            <el-button v-if="row.activeHandoverNo" link type="primary" size="small" @click="openByNo(row.activeHandoverNo)">
              处理交接
            </el-button>
            <el-button v-if="!row.activeHandoverNo && row.status !== 'FAULT'" link type="danger" size="small" @click="quickFault(row)">
              直接报障
            </el-button>
            <span v-if="!row.activeHandoverNo && row.status === 'FAULT'" class="hd-legacy">请等待交接台维修单</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 交接单列表 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        清洗交接单
        <small>最近 50 张</small>
      </div>
      <div class="hd-filter">
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button v-for="t in statusTabs" :key="t.value" :label="t.value">{{ t.label }}</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="handovers" size="small" border stripe>
        <el-table-column prop="handoverNo" label="单号" width="180">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openByNo(row.handoverNo)">{{ row.handoverNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="70">
          <template #default="{ row }">{{ row.handoverType === 'REPAIR' ? '维修' : '清洗' }}</template>
        </el-table-column>
        <el-table-column prop="equipCode" label="设备" width="90" />
        <el-table-column prop="batchNo" label="来源批次" min-width="190">
          <template #default="{ row }">{{ row.batchNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="handoverTagType(row.status)">{{ handoverStatusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="残留" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.residueRisk" size="small" :type="riskType(row.residueRisk)">{{ row.residueRisk }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="cleaner" label="清洗人" width="90" />
        <el-table-column prop="confirmer" label="确认负责人" width="100" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openByNo(row.handoverNo)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 交接详情抽屉 ============ -->
    <el-drawer v-model="drawer" size="62%" :title="drawerTitle" destroy-on-close>
      <div v-if="detail" class="hd-detail">
        <!-- 冲突/开工影响提示 -->
        <el-alert
          v-if="detail.handover.impactNote"
          class="hd-impact"
          :title="detail.handover.resolution === 'FREEZE' ? '本次取舍：确认前报障，立即冻结' : '本次取舍：确认后报障，保留清洗转维修'"
          type="warning"
          :closable="false"
          show-icon
        >
          <div class="hd-impact-body">{{ detail.handover.impactNote }}</div>
        </el-alert>

        <el-alert
          class="hd-impact"
          :title="detail.equipmentAvailable ? '该设备当前可以用于下一批次开工' : '该设备当前不能开工：' + detail.blockedReason"
          :type="detail.equipmentAvailable ? 'success' : 'error'"
          :closable="false"
          show-icon
        />

        <!-- 单据信息 -->
        <section class="tf-card hd-sec">
          <div class="tf-card-title">交接单信息</div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="交接单号">{{ detail.handover.handoverNo }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ detail.handover.handoverType === 'REPAIR' ? '维修交接' : '清洗交接' }}</el-descriptions-item>
            <el-descriptions-item label="设备">{{ detail.handover.equipCode }} {{ detail.handover.equipName }}</el-descriptions-item>
            <el-descriptions-item label="设备类型">{{ detail.handover.equipType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源批次">{{ detail.handover.batchNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源工序">
              {{ detail.teaTypeName ? detail.teaTypeName + ' · ' : '' }}{{ detail.stepName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="建单时间">{{ fmtTime(detail.handover.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag size="small" :type="handoverTagType(detail.handover.status)">{{ detail.statusName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.handover.sourceHandoverNo" label="来源清洗单">
              {{ detail.handover.sourceHandoverNo }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <!-- 清洗人员填写 -->
        <section v-if="detail.handover.handoverType === 'CLEAN'" class="tf-card hd-sec">
          <div class="tf-card-title">
            清洗记录
            <small>清洗人填写残留风险、清洗方式与起止时间</small>
          </div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="残留风险">
              <el-tag v-if="detail.handover.residueRisk" size="small" :type="riskType(detail.handover.residueRisk)">
                {{ detail.handover.residueRisk }}
              </el-tag>
              <span v-else>未填写</span>
            </el-descriptions-item>
            <el-descriptions-item label="残留说明">{{ detail.handover.residueNote || '-' }}</el-descriptions-item>
            <el-descriptions-item label="清洗方式">{{ detail.handover.cleanMethod || '-' }}</el-descriptions-item>
            <el-descriptions-item label="清洗说明">{{ detail.handover.cleanNote || '-' }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ fmtTime(detail.handover.cleanStartedAt) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ fmtTime(detail.handover.cleanFinishedAt) }}</el-descriptions-item>
            <el-descriptions-item label="清洗人">{{ detail.handover.cleaner || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <!-- 故障与维修 -->
        <section v-if="detail.handover.faultNote || detail.handover.handoverType === 'REPAIR'" class="tf-card hd-sec">
          <div class="tf-card-title">故障与维修</div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="故障现象" :span="2">{{ detail.handover.faultNote || '-' }}</el-descriptions-item>
            <el-descriptions-item label="报障人">{{ detail.handover.faultOperator || '-' }}</el-descriptions-item>
            <el-descriptions-item label="报障时间">{{ fmtTime(detail.handover.faultAt) }}</el-descriptions-item>
            <el-descriptions-item label="维修说明" :span="2">{{ detail.handover.repairNote || '-' }}</el-descriptions-item>
            <el-descriptions-item label="维修人">{{ detail.handover.repairer || '-' }}</el-descriptions-item>
            <el-descriptions-item label="维修完成">{{ fmtTime(detail.handover.repairedAt) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <!-- 负责人确认 -->
        <section class="tf-card hd-sec">
          <div class="tf-card-title">负责人确认</div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="确认意见" :span="2">{{ detail.handover.confirmNote || '尚未确认' }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.handover.confirmer || '-' }}</el-descriptions-item>
            <el-descriptions-item label="确认时间">{{ fmtTime(detail.handover.confirmedAt) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <!-- 操作区 -->
        <section class="tf-card hd-sec hd-actions">
          <div class="tf-card-title">
            交接操作
            <small>多人同时操作时仅第一个结论生效，后到者请以刷新后状态为准</small>
          </div>

          <!-- 操作人 -->
          <el-form size="small" label-width="84px">
            <el-form-item label="操作人">
              <el-input v-model="action.operator" placeholder="清洗人 / 维修人 / 负责人签名" style="width: 220px" />
            </el-form-item>
          </el-form>

          <!-- 未开始 → 开始清洗 -->
          <template v-if="detail.handover.status === 'PENDING'">
            <el-form size="small" label-width="84px" class="hd-form">
              <el-form-item label="清洗方式" required>
                <el-select v-model="action.cleanMethod" placeholder="选择清洗方式" style="width: 260px">
                  <el-option v-for="m in cleanMethods" :key="m" :label="m" :value="m" />
                </el-select>
              </el-form-item>
              <el-form-item label="清洗说明">
                <el-input v-model="action.cleanNote" placeholder="可留空" style="width: 360px" />
              </el-form-item>
              <el-form-item label="开始时间">
                <el-date-picker v-model="action.cleanStartedAt" type="datetime" placeholder="默认取当前时间"
                  value-format="YYYY-MM-DDTHH:mm:ss" style="width: 240px" />
              </el-form-item>
            </el-form>
            <div class="hd-btns">
              <el-button type="primary" :loading="acting" @click="doAction('startClean')">开始清洗</el-button>
              <el-button type="danger" plain @click="faultVisible = true">报故障</el-button>
            </div>
          </template>

          <!-- 清洗中 → 完成 / 报障 -->
          <template v-else-if="detail.handover.status === 'CLEANING'">
            <el-form size="small" label-width="84px" class="hd-form">
              <el-form-item label="残留风险" required>
                <el-radio-group v-model="action.residueRisk">
                  <el-radio v-for="r in residueRisks" :key="r" :label="r">{{ r }}</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="残留说明">
                <el-input v-model="action.residueNote" placeholder="茶渣 / 茶渍 / 异味 / 焦化物等" style="width: 360px" />
              </el-form-item>
              <el-form-item label="清洗方式" required>
                <el-select v-model="action.cleanMethod" placeholder="选择清洗方式" style="width: 260px">
                  <el-option v-for="m in cleanMethods" :key="m" :label="m" :value="m" />
                </el-select>
              </el-form-item>
              <el-form-item label="清洗说明">
                <el-input v-model="action.cleanNote" style="width: 360px" />
              </el-form-item>
              <el-form-item label="起止时间">
                <el-date-picker v-model="action.cleanStartedAt" type="datetime" placeholder="开始（默认已记录）"
                  value-format="YYYY-MM-DDTHH:mm:ss" style="width: 200px" />
                <span style="margin: 0 6px">至</span>
                <el-date-picker v-model="action.cleanFinishedAt" type="datetime" placeholder="结束（默认当前）"
                  value-format="YYYY-MM-DDTHH:mm:ss" style="width: 200px" />
              </el-form-item>
            </el-form>
            <div class="hd-btns">
              <el-button type="success" :loading="acting" @click="doAction('finishClean')">标记清洗完成</el-button>
              <el-button type="danger" plain @click="faultVisible = true">报故障</el-button>
            </div>
          </template>

          <!-- 等待确认 → 负责人确认 / 报障 -->
          <template v-else-if="detail.handover.status === 'WAITING_CONFIRM'">
            <el-form size="small" label-width="84px" class="hd-form">
              <el-form-item label="确认意见" required>
                <el-input v-model="action.confirmNote" type="textarea" :rows="2"
                  placeholder="如：目检无残留，试运转正常，同意恢复使用" style="width: 420px" />
              </el-form-item>
            </el-form>
            <div class="hd-btns">
              <el-button type="primary" :loading="acting" @click="doAction('confirm')">负责人确认恢复可用</el-button>
              <el-button type="danger" plain @click="faultVisible = true">报故障（将立即冻结）</el-button>
            </div>
          </template>

          <!-- 故障冻结 → 提示转维修单 -->
          <template v-else-if="detail.handover.status === 'FAULT_FROZEN'">
            <el-alert type="error" :closable="false" show-icon
              title="已立即冻结，本次清洗作废。系统已另开维修单，请在维修单上登记维修结果。" />
          </template>

          <!-- 维修中 → 维修完成 -->
          <template v-else-if="detail.handover.status === 'REPAIR'">
            <el-form size="small" label-width="84px" class="hd-form">
              <el-form-item label="维修说明" required>
                <el-input v-model="action.repairNote" type="textarea" :rows="2"
                  placeholder="更换/检修了什么，是否需要补做清洗" style="width: 420px" />
              </el-form-item>
            </el-form>
            <div class="hd-btns">
              <el-button type="warning" :loading="acting" @click="doAction('repairDone')">维修完成，提交复检</el-button>
            </div>
          </template>

          <!-- 待复检 -->
          <template v-else-if="detail.handover.status === 'REPAIRED'">
            <el-form size="small" label-width="84px" class="hd-form">
              <el-form-item label="复检意见" required>
                <el-input v-model="action.confirmNote" type="textarea" :rows="2"
                  placeholder="复检结论；如需补做清洗请注明" style="width: 420px" />
              </el-form-item>
            </el-form>
            <div class="hd-btns">
              <el-button type="primary" :loading="acting" @click="doAction('confirm')">复检通过，恢复可用</el-button>
              <el-button type="danger" plain @click="faultVisible = true">复检不合格，退回维修</el-button>
            </div>
          </template>

          <!-- 终态 -->
          <template v-else>
            <el-alert type="success" :closable="false" show-icon :title="'本单已办结（' + detail.statusName + '）'" />
          </template>
        </section>

        <!-- 留痕时间线 -->
        <section class="tf-card hd-sec">
          <div class="tf-card-title">操作留痕</div>
          <el-timeline>
            <el-timeline-item
              v-for="log in detail.logs"
              :key="log.id"
              :timestamp="fmtTime(log.createdAt) + ' · ' + (log.operator || '-')"
              :type="logActionType(log.action)"
            >
              <div class="hd-log-action">{{ logActionName(log.action) }}
                <span class="hd-log-state" v-if="log.fromStatus">
                  {{ handoverStatusName(log.fromStatus) }} → {{ handoverStatusName(log.toStatus) }}
                </span>
              </div>
              <div class="hd-log-detail">{{ log.detail }}</div>
            </el-timeline-item>
          </el-timeline>
        </section>
      </div>
    </el-drawer>

    <!-- 报障弹窗 -->
    <el-dialog v-model="faultVisible" title="设备报故障" width="460px">
      <el-form label-width="80px" size="small">
        <el-form-item label="报障人" required>
          <el-input v-model="action.operator" placeholder="报障人签名" />
        </el-form-item>
        <el-form-item label="故障现象" required>
          <el-input v-model="action.faultNote" type="textarea" :rows="3" placeholder="描述故障现象" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="faultVisible = false">取消</el-button>
        <el-button size="small" type="danger" :loading="acting" @click="doAction('reportFault')">确认报障</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { equipmentApi, handoverApi } from '../api'

const STATUS_NAME = {
  PENDING: '清洗未开始',
  CLEANING: '清洗中',
  WAITING_CONFIRM: '等待确认',
  CONFIRMED: '已确认·恢复可用',
  FAULT_FROZEN: '故障冻结',
  REPAIR: '维修中',
  REPAIRED: '维修完成·待复检',
  AVAILABLE: '复检通过·恢复可用'
}
const ACTION_NAME = {
  CREATE: '工序收尾建单',
  START_CLEAN: '开始清洗',
  FINISH_CLEAN: '完成清洗',
  REPORT_FAULT: '报故障',
  REPAIR_DONE: '维修完成',
  CONFIRM_CLEAN: '负责人确认清洗',
  CONFIRM_REPAIR: '负责人复检确认'
}
const statusTabs = [
  { value: 'ACTIVE', label: '进行中' },
  { value: 'PENDING', label: '未开始' },
  { value: 'CLEANING', label: '清洗中' },
  { value: 'WAITING_CONFIRM', label: '等待确认' },
  { value: 'FAULT_FROZEN', label: '故障冻结' },
  { value: 'REPAIR', label: '维修中' },
  { value: 'REPAIRED', label: '待复检' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'AVAILABLE', label: '已恢复' },
  { value: 'ALL', label: '全部' }
]

const board = ref([])
const handovers = ref([])
const equipFilter = ref('ALL')
const typeFilter = ref('')
const statusFilter = ref('ACTIVE')
const residueRisks = ref(['低', '中', '高'])
const cleanMethods = ref(['高压水冲洗', '蒸汽清洗', '人工拆洗', '清洗剂循环', '干法清扫'])

const drawer = ref(false)
const detail = ref(null)
const faultVisible = ref(false)
const acting = ref(false)
const action = reactive({
  operator: '',
  residueRisk: '',
  residueNote: '',
  cleanMethod: '',
  cleanNote: '',
  cleanStartedAt: '',
  cleanFinishedAt: '',
  faultNote: '',
  repairNote: '',
  confirmNote: ''
})

const equipTypes = computed(() => [...new Set(board.value.map((e) => e.equipType).filter(Boolean))])
const blockedCount = computed(() => board.value.filter((e) => !e.available).length)
const availableCount = computed(() => board.value.filter((e) => e.available).length)
const filteredBoard = computed(() =>
  board.value.filter((e) => {
    if (equipFilter.value === 'BLOCKED' && e.available) return false
    if (equipFilter.value === 'AVAILABLE' && !e.available) return false
    if (typeFilter.value && e.equipType !== typeFilter.value) return false
    return true
  })
)
const drawerTitle = computed(() =>
  detail.value ? `交接单 ${detail.value.handover.handoverNo}（${detail.value.statusName}）` : '交接详情'
)

const fmtTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '-')
const handoverStatusName = (s) => STATUS_NAME[s] || s || '-'
const handoverTagType = (s) =>
  ({
    PENDING: 'info',
    CLEANING: 'warning',
    WAITING_CONFIRM: 'warning',
    CONFIRMED: 'success',
    FAULT_FROZEN: 'danger',
    REPAIR: 'danger',
    REPAIRED: 'warning',
    AVAILABLE: 'success'
  }[s] || 'info')
const riskType = (r) => ({ 低: 'success', 中: 'warning', 高: 'danger' }[r] || 'info')
const rawStatusType = (s) => ({ RUNNING: 'warning', IDLE: 'success', CLEANING: 'info', FAULT: 'danger' }[s] || 'info')
const logActionName = (a) => ACTION_NAME[a] || a
const logActionType = (a) =>
  ({ CREATE: 'primary', START_CLEAN: 'warning', FINISH_CLEAN: 'warning', REPORT_FAULT: 'danger', REPAIR_DONE: 'warning', CONFIRM_CLEAN: 'success', CONFIRM_REPAIR: 'success' }[a] || 'primary')

function rowClass({ row }) {
  return row.available ? '' : 'tf-row-warn'
}

async function loadBoard() {
  try {
    const res = await equipmentApi.board()
    board.value = res?.data || []
  } catch (e) {
    ElMessage.error('加载设备看板失败：' + e.message)
  }
}

async function loadHandovers() {
  try {
    const f = statusFilter.value
    if (f === 'ALL') {
      const res = await handoverApi.list()
      handovers.value = res?.data || []
      return
    }
    const params = f === 'ACTIVE'
      ? ['PENDING', 'CLEANING', 'WAITING_CONFIRM', 'FAULT_FROZEN', 'REPAIR', 'REPAIRED']
      : [f]
    const lists = await Promise.all(
      params.map((s) => handoverApi.list(s).then((r) => r?.data || []).catch(() => []))
    )
    const map = new Map()
    lists.flat().forEach((h) => map.set(h.id, h))
    handovers.value = [...map.values()].sort((a, b) => b.id - a.id)
  } catch (e) {
    ElMessage.error('加载交接单失败：' + e.message)
  }
}

async function loadAll() {
  await Promise.all([loadBoard(), loadHandovers()])
}

function resetAction(preserveOperator = true) {
  const op = action.operator
  Object.assign(action, {
    operator: preserveOperator ? op : '',
    residueRisk: '',
    residueNote: '',
    cleanMethod: '',
    cleanNote: '',
    cleanStartedAt: '',
    cleanFinishedAt: '',
    faultNote: '',
    repairNote: '',
    confirmNote: ''
  })
}

async function openByNo(no) {
  try {
    const res = await handoverApi.detail(no)
    detail.value = res?.data || null
    resetAction(false)
    if (detail.value) {
      const h = detail.value.handover
      action.cleanMethod = h.cleanMethod || ''
      action.cleanStartedAt = h.cleanStartedAt ? String(h.cleanStartedAt).slice(0, 19) : ''
      action.residueRisk = h.residueRisk || ''
    }
    drawer.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载交接详情失败')
  }
}

function payload() {
  const p = { operator: action.operator }
  if (action.residueRisk) p.residueRisk = action.residueRisk
  if (action.residueNote) p.residueNote = action.residueNote
  if (action.cleanMethod) p.cleanMethod = action.cleanMethod
  if (action.cleanNote) p.cleanNote = action.cleanNote
  if (action.cleanStartedAt) p.cleanStartedAt = action.cleanStartedAt
  if (action.cleanFinishedAt) p.cleanFinishedAt = action.cleanFinishedAt
  if (action.faultNote) p.faultNote = action.faultNote
  if (action.repairNote) p.repairNote = action.repairNote
  if (action.confirmNote) p.confirmNote = action.confirmNote
  return p
}

async function doAction(kind) {
  if (!detail.value) return
  const no = detail.value.handover.handoverNo
  acting.value = true
  try {
    const res = await handoverApi[kind](no, payload())
    ElMessage.success(res?.message || '操作成功')
    faultVisible.value = false
    await loadAll()
    await openByNo(no)
  } catch (e) {
    // 冲突/状态已变更：提示并刷新到先生效的状态
    ElMessage.error(e.message || '操作失败')
    await loadAll()
    await openByNo(no).catch(() => {})
  } finally {
    acting.value = false
  }
}

async function quickFault(row) {
  let operator = ''
  let faultNote = ''
  try {
    await ElMessageBox.prompt('请填写故障现象（旧设备无交接单，报障后自动开维修单并冻结）', `设备 ${row.equipCode} 报障`, {
      confirmButtonText: '确认报障',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim() ? true : '故障现象不能为空')
    }).then(({ value }) => {
      faultNote = value
    })
    const { value: op } = await ElMessageBox.prompt('报障人签名', '报障人', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim() ? true : '报障人不能为空')
    })
    operator = op
  } catch (e) {
    return
  }
  try {
    const res = await equipmentApi.reportFault(row.equipCode, { operator, faultNote })
    ElMessage.success(res?.message || '已报障冻结')
    await loadAll()
    // 报障后自动打开新生成的维修单
    const active = board.value.find((e) => e.equipCode === row.equipCode)
    if (active?.activeHandoverNo) openByNo(active.activeHandoverNo)
  } catch (e) {
    ElMessage.error(e.message || '报障失败')
    await loadAll()
  }
}

onMounted(loadAll)
</script>

<style scoped>
.hd-policy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-size: 12.5px;
  line-height: 1.8;
  color: #5d6353;
  background: #fdf6ec;
  border-color: #e6c68f;
}

.hd-policy b {
  color: #b45309;
}

.hd-filter {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.hd-block {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hd-block-reason {
  font-size: 12px;
  color: #b3261e;
}

.hd-legacy {
  font-size: 11px;
  color: #9a9788;
}

.hd-detail {
  padding: 0 6px 20px;
}

.hd-sec {
  margin-bottom: 12px;
}

.hd-impact {
  margin-bottom: 12px;
}

.hd-impact-body {
  font-size: 12.5px;
  line-height: 1.7;
}

.hd-form {
  margin-top: 6px;
}

.hd-btns {
  margin-top: 8px;
  padding-left: 84px;
  display: flex;
  gap: 12px;
}

.hd-log-action {
  font-weight: 600;
  font-size: 13px;
}

.hd-log-state {
  margin-left: 8px;
  font-weight: 400;
  font-size: 12px;
  color: #5d6353;
}

.hd-log-detail {
  font-size: 12px;
  color: #5d6353;
  line-height: 1.6;
  margin-top: 2px;
}
</style>
