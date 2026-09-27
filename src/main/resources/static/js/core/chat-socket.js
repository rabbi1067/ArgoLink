const ChatSocket = (() => {
    const subs = new Map();
    let stompClient = null;
    let connected = false;
    let pending = null;

    const available = () => typeof SockJS !== "undefined" && typeof Stomp !== "undefined";

    const connect = () => {
        if (!available()) {
            return Promise.reject(new Error("WebSocket not available"));
        }
        if (connected) return Promise.resolve();
        if (pending) return pending;

        pending = new Promise((resolve, reject) => {
            try {
                const socket = new SockJS("/ws-chat");
                const client = Stomp.over(socket);
                client.heartbeat = { outgoing: 20000, incoming: 0 };
                client.onclose = () => {
                    connected = false;
                    stompClient = null;
                };
                client.connect(
                    {},
                    (frame) => {
                        stompClient = client;
                        connected = true;
                        pending = null;
                        resolve();
                    },
                    (error) => {
                        pending = null;
                        reject(error || new Error("STOMP connection failed"));
                    }
                );
            } catch (error) {
                pending = null;
                reject(error);
            }
        });
        return pending;
    };

    const subscribe = async (conversationId, callback) => {
        if (subs.has(conversationId)) return () => {};
        await connect();
        if (!stompClient) return () => {};
        const subscription = stompClient.subscribe(
            `/topic/conversation.${conversationId}.messages`,
            (message) => {
                try {
                    callback(JSON.parse(message.body));
                } catch (error) {
                    // ignore malformed frames
                }
            }
        );
        subs.set(conversationId, subscription);
        return () => unsubscribe(conversationId);
    };

    const unsubscribe = (conversationId) => {
        const subscription = subs.get(conversationId);
        if (subscription) {
            try {
                subscription.unsubscribe();
            } catch (error) {
                // already closed
            }
            subs.delete(conversationId);
        }
    };

    return {
        connect,
        subscribe,
        unsubscribe,
        available,
        isConnected: () => connected,
    };
})();

window.ChatSocket = ChatSocket;