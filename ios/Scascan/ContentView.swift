//
//  ContentView.swift
//  Scascan
//
//  Created by Nicolò Perri on 14/8/26.
//

import SwiftUI
import ScaScanKit

struct ContentView: View {
    // Smoke test: confirms ScaScanKit's Domain + Data layers are actually
    // importable and usable from the app target, not just linked.
    private let sampleTarget = UserProfileStore.shared.dailyCalorieTarget()

    var body: some View {
        VStack {
            Image(systemName: "fork.knife")
                .imageScale(.large)
                .foregroundStyle(.tint)
            Text("ScaScan")
                .font(.title.bold())
            Text("ScaScanKit linked — default target: \(sampleTarget) kcal")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
