#!/usr/bin/env python3
"""End-to-end workflow on a disposable DEMO_MODE instance only. Uses stdlib.
Creates fictitious records. Do not run against production/real patient databases.
"""
import argparse
import json
import urllib.request
import urllib.error
import urllib.parse
import uuid
from datetime import date

parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://localhost:8088')
parser.add_argument('--admin', required=True)
parser.add_argument('--demo', required=True)
a = parser.parse_args()


def request(path, method='GET', payload=None, token=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(a.url + '/api' + urllib.parse.quote(path, safe='/?=&%'), data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            code = response.status
            result = json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as exc:
        code, result = exc.code, json.loads(exc.read().decode('utf-8'))
    assert code == expected, f'{method} {path}: expected {expected}, got {code}: {result}'
    return result


def post(path, p=None, token=None, expected=200):
    return request(path, 'POST', p or {}, token, expected)


def login(name, password):
    return post('/auth/login', {'username': name, 'password': password})['token']


post('/auth/login', {'username': 'admin', 'password': 'WRONG'}, expected=401)
request('/dashboard', expected=401)
admin = login('admin', a.admin)
registrar = login('registrar', a.demo)
doctor = login('doctor', a.demo)
exam = login('exam', a.demo)
lab = login('lab', a.demo)
procedure = login('procedure', a.demo)
pharmacy = login('pharmacy', a.demo)
request('/audit', token=registrar, expected=403)
meta = request('/meta', token=admin)
patient = post('/patients', {'name': '自动测试虚构患者' + uuid.uuid4().hex[:6], 'gender': '未知'}, registrar)
assert patient['case_number'].startswith('P')
post('/patients', {'name': 'wrong', 'gender': '其他'}, registrar, 400)
post('/patients', {'name': 'unauthorized', 'gender': '男'}, doctor, 403)

schedule = request('/schedules?date=' + date.today().isoformat() + '&period=上午', token=registrar)
assert schedule, 'Expected seeded demo schedule for today'
doc = next(d for d in meta['doctors'] if d['id'] == schedule[0]['employee_id'])


def register():
    payload = {'patientId': patient['id'], 'doctorId': doc['id'],
               'levelId': doc['regist_level_id'], 'settleId': meta['settlements'][0]['id'],
               'date': date.today().isoformat(), 'period': '上午', 'book': True,
               'requestId': uuid.uuid4().hex}
    return post('/registrations', payload, registrar)

reg = register()
assert str(reg['total']) in ('11.00', '11.0', '11')
post(f"/registrations/{reg['id']}/cancel", token=registrar)
post(f"/registrations/{reg['id']}/cancel", token=registrar, expected=409)
reg = register()
record_id = reg['id']
record = {'complaint': '虚构主诉', 'illness': '虚构现病史', 'past': '无'}
request(f'/records/{record_id}', 'PUT', record, doctor)
request(f'/records/{record_id}', token=doctor)
post(f'/registrations/{record_id}/cancel', token=registrar, expected=409)
projects = request('/catalog?type=CHECK', token=doctor)
check_payload = {'registrationId': record_id, 'projectId': projects[0]['id'],
                 'type': 'CHECK', 'purpose': '自动测试', 'requestId': uuid.uuid4().hex}
service = post('/services', check_payload, doctor)
assert post('/services', check_payload, doctor)['id'] == service['id'], 'idempotency key should return same request'
sid = service['id']
post(f'/services/CHECK/{sid}/start', token=exam, expected=409)
charge = next(x for x in request('/charges?q=' + patient['case_number'], token=registrar)
              if x['category'] == 'CHECK' and x['ref_id'] == sid)
post(f"/charges/{charge['id']}/pay", {'method': '现金'}, registrar)
post(f"/charges/{charge['id']}/pay", {'method': '现金'}, registrar, 409)
assert post(f'/services/CHECK/{sid}/start', token=exam)['state'] == 'REGISTERED'
assert post(f'/services/CHECK/{sid}/start', token=exam)['state'] == 'IN_PROGRESS'
post(f'/services/CHECK/{sid}/complete', {'result': '虚构的检查结果'}, exam)
post(f"/charges/{charge['id']}/refund", {'method': '现金'}, registrar, 409)
request(f'/records/{record_id}', token=doctor)
post(f'/records/{record_id}/finalize', {'diagnosis': '教学虚构诊断', 'plan': '教学测试方案'}, doctor)
post(f'/records/{record_id}/finalize', {'diagnosis': '重复诊断', 'plan': '重复'}, doctor, 409)

# After diagnosis, prescription and procedure may be opened.
drugs = request('/drugs', token=doctor)
pres_payload = {'registrationId': record_id, 'requestId': uuid.uuid4().hex, 'items': [{
    'drugId': drugs[0]['id'], 'quantity': 2, 'dosage': '模拟剂量',
    'frequency': '每日一次', 'days': 3, 'advice': '教学用途'}]}
pres = post('/prescriptions', pres_payload, doctor)
assert post('/prescriptions', pres_payload, doctor)['id'] == pres['id'], 'idempotency key should return same prescription'
prescription = next(x for x in request('/prescriptions?q=' + patient['case_number'], token=pharmacy)
                    if x['prescription_id'] == pres['id'])
item_id = prescription['item_id']
post(f'/prescriptions/{item_id}/dispense', token=pharmacy, expected=409)
charge = next(x for x in request('/charges?q=' + patient['case_number'], token=registrar)
              if x['category'] == 'DRUG' and x['ref_id'] == item_id)
post(f"/charges/{charge['id']}/pay", {'method': '微信'}, registrar)
post(f'/prescriptions/{item_id}/dispense', token=pharmacy)
post(f'/prescriptions/{item_id}/dispense', token=pharmacy, expected=409)
post(f"/charges/{charge['id']}/refund", {'method': '微信'}, registrar, 409)
post(f'/prescriptions/{item_id}/return', token=pharmacy)
post(f"/charges/{charge['id']}/refund", {'method': '微信'}, registrar)

projects = request('/catalog?type=PROCEDURE', token=doctor)
service = post('/services', {'registrationId': record_id, 'projectId': projects[0]['id'], 'type': 'PROCEDURE'}, doctor)
charge = next(x for x in request('/charges?q=' + patient['case_number'], token=registrar)
              if x['category'] == 'PROCEDURE' and x['ref_id'] == service['id'])
post(f"/charges/{charge['id']}/pay", {'method': '现金'}, registrar)
post(f"/services/PROCEDURE/{service['id']}/start", token=procedure)
post(f"/services/PROCEDURE/{service['id']}/start", token=procedure)
post(f"/services/PROCEDURE/{service['id']}/complete", {'result': '虚构处置记录'}, procedure)
request('/audit', token=admin)
print('PASS: auth, RBAC, patient, register/refund, visit, service payment gate, examination, diagnosis, prescription, dispense, return/refund, procedure, audit')
