#!/usr/bin/python

import requests
import hashlib
import copy
import hmac
from email.utils import formatdate
import base64
import argparse
from urlparse import urlparse

def sign_request(original_headers, method, path, content_type, content_body, shared_secret):
    """
    returns a dictionary including a suitable authorization header
    :param original_headers: original content headers
    :param content_body: data that is being sent
    :return: new headers dictionary
    """
    new_headers = copy.deepcopy(original_headers)

    content_hasher = hashlib.sha384()
    content_hasher.update(content_body)

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

    print "debug: string to sign: {0}".format(string_to_sign)

    hmaccer = hmac.new(shared_secret, string_to_sign, hashlib.sha384)
    result = hmaccer.hexdigest()
    print "debug: final digest is {0}".format(result)
    new_headers['Authorization'] = "HMAC {0}".format(result)
    return new_headers

#START MAIN
shared_secret = "rubbish"
parser = argparse.ArgumentParser(description="HMAC signing test program")
parser.add_argument("--url", type=str, dest="url", help="URL to contact")
parser.add_argument("--method", type=str, dest="method", help="HTTP method")
parser.add_argument("--secret", type=str, dest="secret", help="shared key")
args = parser.parse_args()

target_uri = urlparse(args.url)

signed_headers = sign_request({}, args.method, target_uri.path, "application/octet-stream", "", args.secret)
print "debug: signed_headers {0}".format(signed_headers)
result = requests.get(args.url, headers=signed_headers)
print "Server returned {0}: {1}".format(result.status_code, result.content)