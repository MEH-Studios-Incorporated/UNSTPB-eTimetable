# Copyright (c) 2026 Multimedia Entertainment Hub LLC
# SPDX-License-Identifier: LicenseRef-Proprietary
#
# Packages the server built by `./gradlew :server:installDist`. The Gradle
# build stays outside the image because the project also configures the
# Android modules, which need the Android SDK.
FROM gcr.io/distroless/java21-debian12:nonroot
COPY build/install/server/lib /opt/etimetable/lib
ENV ETT_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/opt/etimetable/lib/*", "net.mehstudios.etimetable.server.ApplicationKt"]
