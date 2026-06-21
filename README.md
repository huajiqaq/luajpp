# LuaJPP

An Android library for Lua scripting integration.

## Setup

Add the JitPack repository to your root `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency in your module's `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.huajiqaq:luajpp:VERSION'
}
```

## JitPack Publishing

1. Push this project to GitHub
2. Create a release tag (e.g., `v1.0.0`)
3. Go to [jitpack.io](https://jitpack.io) and look up your repo
4. JitPack will build and publish the AAR automatically

The `groupId` published by JitPack is `com.github.huajiqaq`,
and the `artifactId` is `luajpp`.

## License

MIT
