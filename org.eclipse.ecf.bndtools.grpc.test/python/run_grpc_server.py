'''
Created on Mar 28, 2025

@author: slewi
'''
from concurrent import futures
import grpc

import health.health_pb2_grpc as health_grpc
from health.health_pb2 import HealthCheckResponse

class HealthCheckServicer(health_grpc.HealthCheckServicer):
    
    def Check(self, request, 
        target):
        result = HealthCheckResponse()
        result.status = HealthCheckResponse.SERVING
        result.response_message = "Hello request_message=" + request.request_message
        return result

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    health_grpc.add_HealthCheckServicer_to_server(HealthCheckServicer(), server)
    server.add_insecure_port("[::]:50051")
    server.start()
    server.wait_for_termination()
    
if __name__ == '__main__':
    serve()
    