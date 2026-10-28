// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

import Shared
import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(AppInfo.shared.NAME)
                .font(.title2)
                .fontWeight(.semibold)
            Text("Orarul, profesorii și anunțurile FAIMA")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(16)
    }
}

#Preview {
    ContentView()
}
