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

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(container)
                .onOpenURL(perform: handle)
        }
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
        default:
            break
        }
    }
}
