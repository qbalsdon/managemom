#!/bin/bash

PIXEL10_TOKEN="d7Ur0vN2SXy-RMRIXp4vEy:APA91bFBn6A0bpHjckrqvF1cOSxZL5-PAQKKORHTZzlwvTunFcOMp7W_sgqb3IYZYN5kf155Hqk-VfKOmQ14e1KZR2bl3uXfCqANELTlbWs67UZJOjMLTJU"

curl -X POST \
  https://fcm.googleapis.com/v1/projects/managemom-9d6bf/messages:send \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "token": "${PIXEL10_TOKEN}",
      "data": {
        "action": "uninstall_package",
        "package_name": "com.storytoys.marvelhq.googleplay"
      }
    }
  }'
