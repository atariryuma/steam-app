package com.steamdeck.mobile.core.network

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Retrofit CallAdapter for automatic DataResult wrapping
 *
 * Best Practice (2025):
 * - 全APIコール 統一的なerrorハンドリング
 * - HTTP status codefrom適切なAppErrorgenerate
 * - Coroutines suspend 対応
 *
 * Reference:
 * - https://proandroiddev.com/retrofit-with-kotlin-coroutines-and-result-sealed-class-80cc68e12869
 * - https://github.com/square/retrofit/blob/master/retrofit-adapters/
 */
class DataResultCallAdapterFactory : CallAdapter.Factory() {

 override fun get(
  returnType: Type,
  annotations: Array<out Annotation>,
  retrofit: Retrofit
 ): CallAdapter<*, *>? {
  // DataResult<T>type みsupport
  if (getRawType(returnType) != Call::class.java) {
   return null
  }

  val callType = getParameterUpperBound(0, returnType as ParameterizedType)
  if (getRawType(callType) != DataResult::class.java) {
   return null
  }

  val resultType = getParameterUpperBound(0, callType as ParameterizedType)
  return DataResultCallAdapter<Any>(resultType)
 }

 private class DataResultCallAdapter<T>(
  private val successType: Type
 ) : CallAdapter<T, Call<DataResult<T>>> {

  override fun responseType(): Type = successType

  override fun adapt(call: Call<T>): Call<DataResult<T>> {
   return DataResultCall(call)
  }
 }

 private class DataResultCall<T>(
  private val delegate: Call<T>
 ) : Call<DataResult<T>> {

  override fun enqueue(callback: Callback<DataResult<T>>) {
   delegate.enqueue(object : Callback<T> {
    override fun onResponse(call: Call<T>, response: Response<T>) {
     val result = handleResponse(response)
     callback.onResponse(this@DataResultCall, Response.success(result))
    }

    override fun onFailure(call: Call<T>, t: Throwable) {
     val error = AppError.from(t)
     callback.onResponse(this@DataResultCall, Response.success(DataResult.Error(error)))
    }
   })
  }

  override fun execute(): Response<DataResult<T>> {
   return try {
    val response = delegate.execute()
    Response.success(handleResponse(response))
   } catch (t: Throwable) {
    val error = AppError.from(t)
    Response.success(DataResult.Error(error))
   }
  }

  private fun handleResponse(response: Response<T>): DataResult<T> {
   return if (response.isSuccessful) {
    val body = response.body()
    if (body != null) {
     DataResult.Success(body)
    } else {
     // 204 No Contentetc、body null もsuccess扱い
     @Suppress("UNCHECKED_CAST")
     DataResult.Success(Unit as T)
    }
   } else {
    val code = response.code()
    val message = response.errorBody()?.string() ?: response.message()
    val error = AppError.fromHttpCode(code, message)
    DataResult.Error(error)
   }
  }

  override fun clone(): Call<DataResult<T>> = DataResultCall(delegate.clone())

  override fun isExecuted(): Boolean = delegate.isExecuted

  override fun cancel() = delegate.cancel()

  override fun isCanceled(): Boolean = delegate.isCanceled

  override fun request(): Request = delegate.request()

  override fun timeout(): Timeout = delegate.timeout()
 }
}
