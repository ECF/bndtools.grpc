'''
Created on Mar 28, 2025

@author: slewis
'''
import sys
import os 

import grpc_tools.protoc as protoc

def add_to_python_path(new_path):
    existing_path = sys.path
    absolute_path = os.path.abspath(new_path)
    if absolute_path not in existing_path:
        sys.path.append(absolute_path)
    return sys.path

if __name__ == '__main__':
    # the reason for the following is described in this issue
    # https://github.com/grpc/grpc/issues/29459 which has to be fixed in protobuf and apparently hasn't
    # as per https://github.com/protocolbuffers/protobuf/issues/1491
    #
    # the following code is a modification of a workaround suggested here
    # https://github.com/grpc/grpc/issues/29459#issuecomment-1641587881
    # what it does is take the paths from all of the -I<path> arguments and 
    # calls the above add_to_python_path to add the absolute path to the python path for 
    # the subsequent run
    for item in sys.argv:
        if item.startswith('-I'):
            add_to_python_path(item[2:])
    # now call grpc_tools.protoc with the args provided to this script
    # and new sys.path
    protoc.main(sys.argv[1:])

