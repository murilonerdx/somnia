/*
 * Somnia Programming Language
 * Native Network Primitives Implementation
 */

#include "../include/somnia.h"
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>

/* native_net_listen(port: number) -> server_id: number */
Value native_net_listen(Value* args, int arg_count, Env* env) {
    if (arg_count < 1 || args[0].type != VAL_NUMBER) {
        fprintf(stderr, "[NETWORK ERROR] native_net_listen expects (port: number)\n");
        return value_number(-1);
    }

    int port = (int)args[0].as.number;
    int server_fd;
    struct sockaddr_in address;
    int opt = 1;

    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        perror("socket failed");
        return value_number(-1);
    }

    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR | SO_REUSEPORT, &opt, sizeof(opt))) {
        perror("setsockopt");
        return value_number(-1);
    }

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(port);

    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        perror("bind failed");
        return value_number(-1);
    }

    if (listen(server_fd, 3) < 0) {
        perror("listen");
        return value_number(-1);
    }

    return value_number(server_fd);
}

/* native_net_accept(server_id: number) -> client_id: number */
Value native_net_accept(Value* args, int arg_count, Env* env) {
    if (arg_count < 1 || args[0].type != VAL_NUMBER) {
        fprintf(stderr, "[NETWORK ERROR] native_net_accept expects (server_id: number)\n");
        return value_number(-1);
    }

    int server_fd = (int)args[0].as.number;
    struct sockaddr_in address;
    int addrlen = sizeof(address);
    int new_socket;

    if ((new_socket = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen)) < 0) {
        perror("accept");
        return value_number(-1);
    }

    return value_number(new_socket);
}

/* native_net_read(client_id: number) -> data: string */
Value native_net_read(Value* args, int arg_count, Env* env) {
    if (arg_count < 1 || args[0].type != VAL_NUMBER) {
        fprintf(stderr, "[NETWORK ERROR] native_net_read expects (client_id: number)\n");
        return value_null();
    }

    int client_fd = (int)args[0].as.number;
    char buffer[1024 * 32] = {0}; // 32KB buffer for HTTP requests
    int valread = read(client_fd, buffer, sizeof(buffer) - 1);
    
    if (valread < 0) {
        perror("read failed");
        return value_null();
    }

    buffer[valread] = '\0';
    return value_string(buffer);
}

/* native_net_write(client_id: number, data: string) -> success: bool */
Value native_net_write(Value* args, int arg_count, Env* env) {
    if (arg_count < 2 || args[0].type != VAL_NUMBER || args[1].type != VAL_STRING) {
        fprintf(stderr, "[NETWORK ERROR] native_net_write expects (client_id: number, data: string)\n");
        return value_bool(false);
    }

    int client_fd = (int)args[0].as.number;
    const char* data = args[1].as.string;
    
    printf("[DEBUG] Sending %zu bytes. Prefix: '%.20s...'\n", strlen(data), data);
    fflush(stdout);
    
    ssize_t sent = send(client_fd, data, strlen(data), 0);
    return value_bool(sent >= 0);
}

/* native_net_close(id: number) -> success: bool */
Value native_net_close(Value* args, int arg_count, Env* env) {
    if (arg_count < 1 || args[0].type != VAL_NUMBER) {
        fprintf(stderr, "[NETWORK ERROR] native_net_close expects (id: number)\n");
        return value_bool(false);
    }

    int fd = (int)args[0].as.number;
    int res = close(fd);
    return value_bool(res == 0);
}
