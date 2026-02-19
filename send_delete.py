#!/usr/bin/env python3
import argparse
import firebase_admin
from firebase_admin import credentials, messaging

parser = argparse.ArgumentParser(description='Send a delete (uninstall) signal for a package to the app.')
parser.add_argument('package', help='Package name to uninstall (e.g. com.storytoys.marvelhq.googleplay)')
args = parser.parse_args()

# 1. Initialize the Firebase Admin SDK
cred = credentials.Certificate('managemom-9d6bf-firebase-adminsdk-fbsvc-77b7b32eb8.json')
firebase_admin.initialize_app(cred)

print("Firebase Admin SDK initialized successfully.")

# 2. Define the FCM registration token of the target device
# This is the token you obtained from your Android app.
PIXEL10_TOKEN = "d7Ur0vN2SXy-RMRIXp4vEy:APA91bFBn6A0bpHjckrqvF1cOSxZL5-PAQKKORHTZzlwvTunFcOMp7W_sgqb3IYZYN5kf155Hqk-VfKOmQ14e1KZR2bl3uXfCqANELTlbWs67UZJOjMLTJU"
registration_token = PIXEL10_TOKEN

# 3. Define the data payload for the message
message_data = {
    'action': 'uninstall_package',
    'package_name': args.package,
    'packages': args.package,  # app also reads this key
}

# 4. Construct the message
# High priority so FCM delivers when the app is not running (can wake the device).
message = messaging.Message(
    data=message_data,
    token=registration_token,
    android=messaging.AndroidConfig(priority='high'),
)

# 5. Send the message
try:
    response = messaging.send(message)
    print(f"Successfully sent message: {response}")
    # The 'response' will typically be a message ID string.
except Exception as e:
    print(f"Error sending message: {e}")
    # For more detailed error handling, you can catch specific exceptions like
    # firebase_admin.exceptions.FirebaseError or firebase_admin.messaging.UnregisteredError
    # as mentioned in the documentation.

# Example of more robust error handling (optional, but good practice):
try:
    messaging.send(message)
except messaging.UnregisteredError:
    print('App instance has been unregistered. Remove token from your database.')
    # You would typically remove this 'registration_token' from your database here.
except firebase_admin.exceptions.UnavailableError:
    print('FCM service is temporarily unavailable. Schedule for retry.')
    # Implement retry logic here.
except firebase_admin.exceptions.FirebaseError as ex:
    print(f'Failed to send notification: {ex}')
    if ex.http_response is not None:
        print(f'FCM service responded with HTTP {ex.http_response.status_code}')
        print(f'Response content: {ex.http_response.content}')
