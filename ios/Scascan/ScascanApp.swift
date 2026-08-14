//
//  ScascanApp.swift
//  Scascan
//
//  Created by Nicolò Perri on 14/8/26.
//

import SwiftUI
import ScaScanKit

/// Mirrors Android's `ScaScanApplication.kt` + `MainActivity.kt` entry point.
@main
struct ScascanApp: App {
    @State private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(container)
        }
    }
}
