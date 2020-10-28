#!/usr/bin/python3

import requests
import hashlib
import copy
import hmac
from email.utils import formatdate
import base64
import argparse
from urllib.parse import urlparse
import sys

def sign_request(original_headers:dict, method:str, path:str, content_type:str, content_body:str, shared_secret:str) -> dict:
    """
    returns a dictionary including a suitable authorization header
    :param original_headers: original content headers
    :param content_body: data that is being sent
    :return: new headers dictionary
    """
    new_headers = copy.deepcopy(original_headers)

    content_hasher = hashlib.sha384()
    content_hasher.update(content_body.encode("UTF-8"))

    nowdate = formatdate(usegmt=True)
    checksumstring = content_hasher.hexdigest()
    new_headers['Digest'] = "SHA-384=" + checksumstring
    new_headers['Content-Length'] = str(len(content_body))
    new_headers['Content-Type'] = content_type
    new_headers['Date'] = nowdate

    string_to_sign = """{path}\n{date}\n{content_type}\n{checksum}\n{method}""".format(
        date=nowdate,content_type=content_type,checksum=checksumstring,
        method=method,path=path
    )

    print("debug: string to sign: {0}".format(string_to_sign))

    hmaccer = hmac.new(shared_secret.encode("UTF-8"), string_to_sign.encode("UTF-8"), hashlib.sha384)
    result = hmaccer.hexdigest()
    print("debug: final digest is {0}".format(result))
    new_headers['Authorization'] = "HMAC {0}".format(result)
    return new_headers

#START MAIN
parser = argparse.ArgumentParser(description="HMAC signing test program")
parser.add_argument("--url", type=str, dest="url", help="URL to contact")
parser.add_argument("--method", type=str, dest="method", help="HTTP method")
parser.add_argument("--secret", type=str, dest="secret", help="shared key")
args = parser.parse_args()

if not args.url:
    print("You must specify --url on the commandline")
    sys.exit(1)
if not args.method:
    print("You must specify --method on the commandline")
    sys.exit(1)
if not args.secret:
    print("You must specify --secret on the commandline")
    sys.exit(1)

target_uri = urlparse(args.url)
signing_path = target_uri.path
if target_uri.query != "":
    signing_path += "?" + target_uri.query

signed_headers = sign_request({}, args.method, signing_path, "application/octet-stream", "", args.secret)
print("debug: signed_headers {0}".format(signed_headers))
result = requests.get(args.url, headers=signed_headers, verify=False)
print("Server returned {0}: {1}".format(result.status_code, result.content))