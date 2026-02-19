import firebase_admin
from firebase_admin import credentials, messaging

# 1. Initialize the Firebase Admin SDK
# Replace 'path/to/your-service-account-file.json' with the actual path to your downloaded JSON key file.
cred = credentials.Certificate('path/to/your-service-account-file.json')
firebase_admin.initialize_app(cred)

print("Firebase Admin SDK initialized successfully.")

# 2. Define the FCM registration token of the target device
# This is the token you obtained from your Android app.
PIXEL10_TOKEN="d7Ur0vN2SXy-RMRIXp4vEy:APA91bFBn6A0bpHjckrqvF1cOSxZL5-PAQKKORHTZzlwvTunFcOMp7W_sgqb3IYZYN5kf155Hqk-VfKOmQ14e1KZR2bl3uXfCqANELTlbWs67UZJOjMLTJU"
registration_token = PIXEL10_TOKEN

# 3. Define the data payload for the message
# This data tells your Android app what action to perform (uninstall_package)
# and which package to target (com.storytoys.marvelhq.googleplay).
message_data = {
    'action': 'uninstall_package',
    'package_name': 'com.storytoys.marvelhq.googleplay'
}

# 4. Construct the message
# We are sending a data message, so we use the 'data' field.
message = messaging.Message(
    data=message_data,
    token=registration_token,
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

