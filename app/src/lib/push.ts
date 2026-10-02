import { Capacitor } from '@capacitor/core'
import { PushNotifications } from '@capacitor/push-notifications'

/**
 * Registers for FCM push on native builds only - a no-op in the browser, where
 * @capacitor/push-notifications has nothing to talk to. Call once after a successful sign-in.
 *
 * The token this produces is only logged for now, not sent anywhere: there is no
 * device-token storage or a real push-sending backend yet (that needs its own Firebase project
 * and service account, which - like MSG91 and WhatsApp Cloud API - is a manual setup step, not
 * something this call can create). See docs/features for the mobile-wrapper feature note.
 */
export async function registerForPush(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return

  const permission = await PushNotifications.checkPermissions()
  if (permission.receive !== 'granted') {
    const requested = await PushNotifications.requestPermissions()
    if (requested.receive !== 'granted') return
  }

  await PushNotifications.register()
}

/** Wire these up once, e.g. in App.tsx, so registration and incoming pushes are visible while testing. */
export function addPushListeners(): void {
  if (!Capacitor.isNativePlatform()) return

  PushNotifications.addListener('registration', (token) => {
    // eslint-disable-next-line no-console
    console.log('FCM token (not sent anywhere yet):', token.value)
  })
  PushNotifications.addListener('registrationError', (err) => {
    console.error('Push registration failed:', err)
  })
  PushNotifications.addListener('pushNotificationReceived', (notification) => {
    console.log('Push received:', notification)
  })
}
