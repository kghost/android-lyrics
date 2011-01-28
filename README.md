## Requirement ##

Android 2.2

## How to build ##

Build apk

    mvn install

or put signkey at $(SOURCE_DIR)/signkey.keystore and run

    mvn install -Djarsigner.storepass=KEYPASS

## Known Issue ##

