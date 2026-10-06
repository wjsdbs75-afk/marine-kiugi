#!/usr/bin/env python3
"""App Store Connect API 작은 도구 (Actions 에서만 쓴다).

  asc.py ensure-bundle-id   번들 ID 를 애플에 등록해 둔다 (이미 있으면 그대로). App Store Connect 에서
                            "새로운 앱" 을 만들 때 이 번들 ID 가 목록에 나오게 하려고 쓴다.
  asc.py has-app            그 번들 ID 로 만든 앱이 App Store Connect 에 있는지 (없으면 종료 코드 3).

환경 변수: KEY_ID, ISSUER, KEY_PATH(.p8 경로), BUNDLE_ID
"""
import json, os, sys, time, urllib.parse, urllib.request
import jwt   # PyJWT (cryptography 필요)

API = 'https://api.appstoreconnect.apple.com/v1/'


def token():
    key = open(os.environ['KEY_PATH']).read()
    now = int(time.time())
    return jwt.encode({'iss': os.environ['ISSUER'], 'iat': now, 'exp': now + 600, 'aud': 'appstoreconnect-v1'},
                      key, algorithm='ES256', headers={'kid': os.environ['KEY_ID'], 'typ': 'JWT'})


def call(method, path, body=None):
    req = urllib.request.Request(API + path, method=method, data=json.dumps(body).encode() if body else None,
                                 headers={'Authorization': 'Bearer ' + token(), 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        print(f'{method} {path} → HTTP {e.code}: {e.read().decode()[:800]}', file=sys.stderr)
        raise SystemExit(1)


def main():
    bid = os.environ['BUNDLE_ID']
    q = urllib.parse.quote(bid)
    if sys.argv[1] == 'ensure-bundle-id':
        found = [d for d in call('GET', f'bundleIds?filter[identifier]={q}')['data'] if d['attributes']['identifier'] == bid]
        if found:
            print(f'번들 ID {bid} 는 이미 등록되어 있습니다.')
            return
        call('POST', 'bundleIds', {'data': {'type': 'bundleIds', 'attributes': {
            'identifier': bid, 'name': 'Marine Kiugi', 'platform': 'IOS'}}})
        print(f'번들 ID {bid} 를 등록했습니다.')
    elif sys.argv[1] == 'has-app':
        apps = call('GET', f'apps?filter[bundleId]={q}')['data']
        if not apps:
            print(f'App Store Connect 에 번들 ID {bid} 로 만든 앱이 아직 없습니다.')
            raise SystemExit(3)
        print(f"앱 있음: {apps[0]['attributes'].get('name')} (id {apps[0]['id']})")


if __name__ == '__main__':
    main()
