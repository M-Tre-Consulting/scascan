//
//  ScascanApp.swift
//  Scascan
//
//  Created by Nicolò Perri on 14/8/26.
//

import SwiftUI
import ScaScanKit

/// Mirrors Android's `ScaScanApplication.kt` + `MainActivity.kt` entry point,
/// including `handleIntent`'s deep-link handling for widget taps / quick actions.
@main
struct ScascanApp: App {
    @State private var container = AppContainer()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(container)
                .onOpenURL(perform: handle)
                .task {
                    NotificationHelper.refreshHydrationScheduleIfNeeded()
                    consumePendingVoiceLogRequest()
                }
        }
        .onChange(of: scenePhase) { _, phase in
            // Tops up today's hydration schedule whenever it's stale (a new
            // calendar day since it was last computed) — see
            // `NotificationHelper`'s doc comment for why this, rather than a
            // single set-and-forget repeating trigger, is needed to support
            // pushing reminders back when water gets logged.
            if phase == .active {
                NotificationHelper.refreshHydrationScheduleIfNeeded()
                consumePendingVoiceLogRequest()
            }
        }
    }

    /// Checks the flag `StartVoiceLogIntent` (the Control Center button)
    /// leaves behind in the shared App Group store, since it runs in the
    /// widget extension's own process and can't reach this app's live
    /// `AppContainer` directly. If set, opens straight into voice logging.
    private func consumePendingVoiceLogRequest() {
        guard container.profileStore.pendingVoiceLogRequest else { return }
        container.profileStore.pendingVoiceLogRequest = false
        container.deepLinkTab = 0
        container.deepLinkRoute = .voice
    }

    /// Handles `scascan://log`, `scascan://camera`, `scascan://barcode`,
    /// `scascan://search` — the widget's tap targets. Mirrors Android's
    /// `ACTION_OPEN_LOG` / `ACTION_QUICK_SCAN` / `ACTION_QUICK_BARCODE` /
    /// `ACTION_QUICK_SEARCH` intent actions.
    private func handle(_ url: URL) {
        switch url.host {
        case "log":
            container.deepLinkTab = 1
        case "camera":
            container.deepLinkTab = 0
            container.deepLinkRoute = .camera
        case "barcode":
            container.deepLinkTab = 0
            container.deepLinkRoute = .barcode
        case "search":
            container.deepLinkTab = 0
            container.deepLinkRoute = .search
        case "voice":
            container.deepLinkTab = 0
            container.deepLinkRoute = .voice
        default:
            break
        }
    }
}
