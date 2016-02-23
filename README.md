
Background Video Recorder
===================================

## Main features

* When phone blocks video record goes on.

* Records in 1080 be default (cf. [#1](https://github.com/ulysses4ever/Background-VideoRecorder/issues/1)).

## Credits

This code is mostly compiled from three sources:

* [Android MediaRecorder Sample](https://github.com/googlesamples/android-MediaRecorder).

* [SO answer on using Android `Service` for recording video](http://stackoverflow.com/a/16552892/465100).

* Android 5+ gets much more complicated in working with SD storage. We followed [official documentation](http://developer.android.com/guide/topics/providers/document-provider.html) as accurate as we could. This complicated architecture considerably (adds one more asynchronous event: user should choose output file placement on his own).

