"""
Autorizador Lambda para AWS API Gateway (HTTP API) que valida tokens JWT
emitidos por Azure AD B2C (Grupo 13 - Exp2 S6).

- Sin dependencias externas: verifica la firma RS256 con Python puro
  (modulo y exponente del JWKS de la policy de B2C).
- Valida firma, expiracion (exp/nbf), emisor (iss) y audiencia (aud).
- Formato de respuesta "Simple" del autorizador Lambda de HTTP API:
  devuelve {"isAuthorized": true|false}.
"""
import json
import base64
import hashlib
import time
import urllib.request

# ----- Configuracion (valores del tenant del Grupo 13) -----
ISSUER = "https://duocgrupo13.b2clogin.com/3f8624f7-de01-47f3-9fd0-f834f87dc061/v2.0/"
AUDIENCE = "259dff0d-8d49-41ef-8f85-18bebb472ec0"
JWKS_URL = "https://duocgrupo13.b2clogin.com/duocgrupo13.onmicrosoft.com/B2C_1_signupsignin/discovery/v2.0/keys"

# Prefijo DER de DigestInfo para SHA-256 (PKCS#1 v1.5)
_SHA256_DER_PREFIX = bytes.fromhex("3031300d060960864801650304020105000420")
_jwks_cache = {"keys": None, "ts": 0.0}


def _b64url_decode(data: str) -> bytes:
    data = data.encode("ascii")
    data += b"=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data)


def _get_jwks(force=False):
    now = time.time()
    if force or _jwks_cache["keys"] is None or (now - _jwks_cache["ts"]) > 3600:
        with urllib.request.urlopen(JWKS_URL, timeout=5) as resp:
            _jwks_cache["keys"] = json.loads(resp.read().decode("utf-8"))["keys"]
            _jwks_cache["ts"] = now
    return _jwks_cache["keys"]


def _find_key(kid):
    for k in _get_jwks():
        if k.get("kid") == kid:
            return k
    for k in _get_jwks(force=True):  # refresca una vez por si rotaron las llaves
        if k.get("kid") == kid:
            return k
    return None


def _rsa_pkcs1_verify(signing_input: bytes, signature: bytes, n_b64: str, e_b64: str) -> bool:
    n = int.from_bytes(_b64url_decode(n_b64), "big")
    e = int.from_bytes(_b64url_decode(e_b64), "big")
    s = int.from_bytes(signature, "big")
    if s >= n:
        return False
    m = pow(s, e, n)
    k = (n.bit_length() + 7) // 8
    em = m.to_bytes(k, "big")
    digest = hashlib.sha256(signing_input).digest()
    t = _SHA256_DER_PREFIX + digest
    ps_len = k - 3 - len(t)
    if ps_len < 8:
        return False
    expected = b"\x00\x01" + (b"\xff" * ps_len) + b"\x00" + t
    return em == expected


def _validate(token: str) -> bool:
    parts = token.split(".")
    if len(parts) != 3:
        return False
    header = json.loads(_b64url_decode(parts[0]))
    payload = json.loads(_b64url_decode(parts[1]))
    signature = _b64url_decode(parts[2])
    signing_input = (parts[0] + "." + parts[1]).encode("ascii")

    if header.get("alg") != "RS256":
        return False
    key = _find_key(header.get("kid"))
    if not key:
        return False
    if not _rsa_pkcs1_verify(signing_input, signature, key["n"], key["e"]):
        return False

    now = time.time()
    if float(payload.get("exp", 0)) < now:
        return False
    if float(payload.get("nbf", 0)) > now + 60:
        return False
    if payload.get("iss") != ISSUER:
        return False
    aud = payload.get("aud")
    if isinstance(aud, list):
        if AUDIENCE not in aud:
            return False
    elif aud != AUDIENCE:
        return False
    return True


def lambda_handler(event, context):
    # Obtener el token desde identitySource o las cabeceras
    token = None
    ident = event.get("identitySource")
    if ident:
        token = ident[0]
    if not token:
        headers = event.get("headers") or {}
        token = headers.get("authorization") or headers.get("Authorization")
    if token and token.lower().startswith("bearer "):
        token = token[7:].strip()

    try:
        authorized = bool(token) and _validate(token)
    except Exception as ex:  # noqa
        print("Error validando token:", repr(ex))
        authorized = False

    return {"isAuthorized": authorized}
