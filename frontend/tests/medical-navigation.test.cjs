const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const Vue = require('vue')
const compiler = require('vue-template-compiler')
const source = fs.readFileSync(require('node:path').join(__dirname, '../src/views/Medical.vue'), 'utf8')
const parsed = compiler.parseComponent(source)
function instance(api) {
  const context = { module: { exports: {} }, api, window: { scrollTo() {} } }
  vm.runInNewContext(parsed.script.content.replace(/import api from ['"]\.\.\/api['"];?/, '').replace('export default', 'module.exports ='), context)
  const options = context.module.exports
  const compiled = compiler.compileToFunctions(parsed.template.content)
  return new Vue({ ...options, created: [], render: compiled.render, staticRenderFns: compiled.staticRenderFns,
    methods: { ...options.methods, $t: value => value, localizedCell: () => '' } })
}
function text(node) { return (node.text || '') + (node.children || []).map(text).join('') }
test('opening a patient shows the workspace instead of leaving the queue above it', () => {
  const component = instance({})
  component.active = { id: 4, real_name: '王静怡', case_number: 'P00000004', visit_state: 'IN_PROGRESS' }
  const rendered = text(component._render())
  assert.ok(rendered.includes('当前患者：王静怡'))
  assert.equal(rendered.includes('我的接诊患者'), false)
})
test('failed record loading keeps the queue visible and reports the error', async () => {
  const component = instance({ get: async () => { throw new Error('读取失败') } })
  const errors = []
  component.$message = { error: message => errors.push(message) }
  await component.select({ id: 4 })
  assert.equal(component.active, null)
  assert.deepEqual(errors, ['读取失败'])
})
