#!/usr/bin/env python3
"""Read-only public CORS smoke check. Does not log in or send application writes."""
from urllib.error import HTTPError
from urllib.request import Request, urlopen


URL = 'https://api.chrono-logisch.ch/api/auth/me'


def request(origin, method=None, headers='authorization,content-type,x-chrono-company'):
    values = {'Origin': origin}
    if method:
        values.update({'Access-Control-Request-Method': method, 'Access-Control-Request-Headers': headers})
    try:
        return urlopen(Request(URL, method='OPTIONS' if method else 'GET', headers=values), timeout=15)
    except HTTPError as response:
        return response


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    for origin in ['https://chrono-logisch.ch', 'https://www.chrono-logisch.ch']:
        for method in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']:
            requested = 'authorization,content-type,x-chrono-company,idempotency-key'
            with request(origin, method, requested) as response:
                check(response.status in (200, 204), f'{origin} {method}: preflight rejected')
                check(response.headers.get_all('Access-Control-Allow-Origin') == [origin], 'Missing/duplicate allow-origin')
                allowed = {value.strip().lower() for value in response.headers.get('Access-Control-Allow-Headers', '').split(',')}
                check(set(requested.split(',')) <= allowed, 'Company/idempotency header not allowed')
                check(method in response.headers.get('Access-Control-Allow-Methods', '').replace(' ', '').split(','), 'Method not allowed')
                check(response.headers.get('Access-Control-Allow-Credentials') == 'true', 'Credentials not allowed')
        with request(origin) as response:
            check(response.status in (401, 403), 'Profile must still require authentication')
            check(response.headers.get_all('Access-Control-Allow-Origin') == [origin], 'Auth error has missing/duplicate allow-origin')
        print(f'[OK] {origin}: company preflights and authentication error headers')
    with request('https://untrusted.example', 'GET') as response:
        check(response.status == 403 and response.headers.get('Access-Control-Allow-Origin') is None, 'Untrusted origin allowed')
    with request('https://chrono-logisch.ch', 'GET', 'x-unapproved-header') as response:
        check(response.status == 403, 'Unapproved request header allowed')
    print('[OK] Untrusted origins and unapproved headers rejected')


if __name__ == '__main__':
    main()
