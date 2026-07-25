package com.antigravity.mes.hrms;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;

/**
 * hrms.thegttech.com's DNS returns both a public and a private-range IP for the same
 * hostname; the private one is unreachable from this network and simply hangs until timeout.
 * Trying addresses one at a time (as a naive fallback would) means every call pays that full
 * timeout whenever DNS happens to hand back the unreachable address first - order isn't
 * guaranteed to stay stable. This factory instead races a connection attempt to every resolved
 * address in parallel (real Happy Eyeballs / RFC 8305) and uses whichever connects first,
 * so latency is bounded by the fastest reachable address regardless of DNS ordering.
 */
class HappyEyeballsSslSocketFactory extends SSLSocketFactory {

    private static final int CONNECT_TIMEOUT_MS = 4_000;

    private final SSLSocketFactory delegate;

    HappyEyeballsSslSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return delegate.createSocket(connectReachable(host, port), host, port, true);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return delegate.createSocket(connectReachable(host, port), host, port, true);
    }

    private Socket connectReachable(String host, int port) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 1) {
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(addresses[0], port), CONNECT_TIMEOUT_MS);
            return raw;
        }

        ExecutorService executor = Executors.newFixedThreadPool(addresses.length);
        try {
            CompletionService<Socket> completionService = new ExecutorCompletionService<>(executor);
            List<Future<Socket>> futures = new java.util.ArrayList<>();
            for (InetAddress address : addresses) {
                futures.add(completionService.submit(() -> {
                    Socket raw = new Socket();
                    raw.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
                    return raw;
                }));
            }

            IOException lastFailure = null;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    Future<Socket> completed = completionService.poll(CONNECT_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS);
                    if (completed == null) break;
                    Socket winner = completed.get();
                    for (Future<Socket> other : futures) {
                        if (other != completed) other.cancel(true);
                    }
                    return winner;
                } catch (ExecutionException e) {
                    lastFailure = e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while connecting to " + host, e);
                }
            }
            throw new IOException("No reachable address for " + host + " among " + addresses.length + " resolved", lastFailure);
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return delegate.createSocket(s, host, port, autoClose);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }
}
