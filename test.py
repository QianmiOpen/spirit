#!/usr/bin/python
#-*-coding:utf-8-*-

import httplib
import urllib

'''
Return information to spirit http server on port 2551
'''
httpClient = None  
try:  
    params = urllib.urlencode({'name': 'tom', 'age': 22})
    headers = {"Content-type": "application/json"  
                    , "Accept": "text/plain"}  
  
    httpClient = httplib.HTTPConnection("localhost", 2551, timeout=30)  
    httpClient.request("POST", "/job/11111" , params, headers)  
  
    response = httpClient.getresponse()  
    print response.status  
    print response.reason  
    print response.read()  
    print response.getheaders() #获取头信息  
except Exception, e:  
    print e  
finally:  
    if httpClient:  
        httpClient.close()

