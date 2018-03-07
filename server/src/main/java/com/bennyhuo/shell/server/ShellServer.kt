/*
 * Copyright (C) 2017 Zane.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bennyhuo.shell.server

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Created by Zane on 2017/10/16.
 * Email: zanebot96@gmail.com
 */

private const val SERVER_PORT = 62741
private const val CMD_EXIT = "exit\n"

class ShellServer(name: String) : Thread(name) {
    private val executor = Executors.newCachedThreadPool()

    override fun run() {
        try {
            val serverSocket = ServerSocket(SERVER_PORT)

            debug("Server Socket open success: ${serverSocket.inetAddress}")
            while (true) {
                val socket: Socket
                try {
                    socket = serverSocket.accept()
                    debug("Client Socket accept success from: ${socket.inetAddress}")
                    process(socket)
                } catch (e: IOException) {
                    warn("Socket error in serverSocket accept(): ${e.message}")
                    if (serverSocket.isClosed) {
                        break
                    }
                }
            }
        } catch (e: IOException) {
            warn("Socket error when open: ${e.message}")
            return
        }
    }

    @Throws(IOException::class)
    private fun process(socket: Socket) {
        val shellProcess = ProcessBuilder("sh").start()
        val safeClose = fun(block: () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
            } finally {
                try {
                    shellProcess.destroy()
                    socket.close()
                } catch (e: Exception) {
                }
            }
        }

        executor.execute {
            safeClose {
                socket.getInputStream().bufferedReader().forEachLine {
                    debug("[ShellInput] $it")
                    if (it.equals(CMD_EXIT)){
                        shellProcess.outputStream.close()
                    } else {
                        shellProcess.outputStream.write("$it\n".toByteArray())
                        shellProcess.outputStream.flush()
                    }
                }
                shellProcess.outputStream.close()
            }
        }

        executor.execute {
            safeClose {
                shellProcess.inputStream.bufferedReader().forEachLine {
                    warn("[ShellOut] $it")
                    socket.getOutputStream().write("$it\n".toByteArray())
                    socket.getOutputStream().flush()
                }
                shellProcess.destroy()
                warn("Destroy shell process.")
            }
        }
    }
}
