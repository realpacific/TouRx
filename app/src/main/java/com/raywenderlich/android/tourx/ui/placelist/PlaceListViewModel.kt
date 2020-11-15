/*
 * Copyright (c) 2019 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.tourx.ui.placelist

import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import com.raywenderlich.android.tourx.BaseViewModel
import com.raywenderlich.android.tourx.LOG_TAG
import com.raywenderlich.android.tourx.entities.Places
import com.raywenderlich.android.tourx.networking.ApiService
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.zipWith
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class PlaceListViewModel
@ViewModelInject constructor(private val service: ApiService) : BaseViewModel<Places>() {

  /**
   * Uses [Observable.ambWith] to combine two [Observable] so only the first [Observable] to emit gets relayed.
   */
  fun loadTheQuickestOne() {
    startLoading()
    val earthSource = service.fetchEarthPlaces()
    val marsSource = service.fetchMarsPlaces()
        .doOnDispose { Log.i(LOG_TAG, "Disposing mars sources") }

    earthSource.ambWith(marsSource)
        .subscribeOn(Schedulers.io())
        .doOnSubscribe { recordStartTime() }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy(onSuccess = onSuccessHandler, onError = onErrorHandler)
        .addTo(disposable)
  }

  /**
   * Uses [Observable.zip] underneath so waits for all the [Observable] to complete.
   */
  fun loadAllAtOnce() {
    startLoading()
    service.fetchEarthPlaces()
        .zipWith(service.fetchMarsPlaces())
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSubscribe { recordStartTime() }
        .map {
          return@map listOf(*it.first.toTypedArray(), *it.second.toTypedArray())
        }
        .subscribeBy(onSuccess = onSuccessHandler, onError = onErrorHandler)
        .addTo(disposable)
  }


  /**
   * Emits as soon as there is data available.
   */
  fun loadOnReceive() {
    startLoading()
    service.fetchEarthPlaces()
        .mergeWith(service.fetchMarsPlaces())
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSubscribe { recordStartTime() }
        .subscribeBy(
            onNext = onDataLoadHandler,
            onError = onErrorHandler,
            onComplete = onCompleteHandler
        ).addTo(disposable)
  }


  /**
   * Waits for all the [Observable] to complete before relaying the erroneous [Observable] down the chain.
   */
  fun loadExperimental() {
    startLoading()
    Single.mergeDelayError(
        service.fetchFromExperimentalApi(),
        service.fetchMarsPlaces(),
        service.fetchEarthPlaces()
    ).subscribeOn(Schedulers.io())
        .doOnSubscribe { recordStartTime() }
        .observeOn(AndroidSchedulers.mainThread(), true)
        .subscribeBy(
            onNext = onDataLoadHandler,
            onComplete = onCompleteHandler,
            onError = onErrorHandler
        )
        .addTo(disposable)
  }

  fun demonstrateSwitchOnNext() {
    disposeCurrentlyRunningStreams()

    // 1
    val outerSource = Observable.interval(3, TimeUnit.SECONDS)
        .doOnNext {
          Log.i(LOG_TAG, "Emitted by OuterSource: $it")
        }

    // 2
    val innerSource = Observable.interval(1, TimeUnit.SECONDS)
        .doOnSubscribe {
          Log.i(LOG_TAG, "Starting InnerSource")
        }

    // 3
    Observable.switchOnNext(
        outerSource.map { return@map innerSource }
    )
        .doOnNext {
          Log.i(LOG_TAG, "Relayed items $it")
        }
        .subscribeOn(Schedulers.single())
        .observeOn(Schedulers.single())
        .subscribe().addTo(disposable)
  }

  fun demonstrateJoinBehavior() {
    disposeCurrentlyRunningStreams()

    // 1
    val firstObservable = Observable.interval(1000, TimeUnit.MILLISECONDS)
        .map {
          return@map "SOURCE-1 $it"
        }
    // 2
    val secondObservable = Observable.interval(3000, TimeUnit.MILLISECONDS)
        .map { return@map "SOURCE-2 $it" }

    // 3
    val firstWindow = Function<String, Observable<Long>> {
      Observable.timer(0, TimeUnit.SECONDS)
    }
    val secondWindow = Function<String, Observable<Long>> {
      Observable.timer(0, TimeUnit.SECONDS)
    }

    // 4
    val resultSelector = BiFunction<String, String, String> { t1, t2 ->
      return@BiFunction "$t1, $t2"
    }

    //5
    firstObservable.join(secondObservable, firstWindow, secondWindow, resultSelector)
        .doOnNext {
          Log.i(LOG_TAG, it)
        }
        .subscribeOn(Schedulers.single())
        .observeOn(Schedulers.single())
        .subscribe().addTo(disposable)
  }


  fun demonstrateGroupJoin() {
    disposeCurrentlyRunningStreams()
    // 1
    val leftSource = Observable.interval(1, TimeUnit.SECONDS)
        .map { return@map "SOURCE-1 $it" }

    // 2
    val rightSource = Observable.interval(5, TimeUnit.SECONDS)
        .map { return@map "SOURCE-2 $it" }

    // 3
    val leftWindow = Function<String, Observable<Long>> {
      Observable.timer(0, TimeUnit.SECONDS)
    }

    // 4
    val rightWindow = Function<String, Observable<Long>> {
      Observable.timer(3, TimeUnit.SECONDS)
    }

    // 5
    val resultSelector = BiFunction<String, Observable<String>, Observable<Pair<String, String>>> { t1, t2 ->
      return@BiFunction t2.map {
        return@map Pair(t1, it)
      }
    }

    // 6
    leftSource.groupJoin(rightSource, leftWindow, rightWindow, resultSelector)
        .concatMap {
          return@concatMap it
        }
        .subscribeOn(Schedulers.single())
        .observeOn(Schedulers.single())
        .doOnNext {
          Log.i(LOG_TAG, it.toString())
        }
        .subscribe().addTo(disposable)
  }
}