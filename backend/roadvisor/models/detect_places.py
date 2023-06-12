import os
import sys
import numpy as np
os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = os.getcwd() + "/roadvisorml-23b634c71823.json"
import base64

from typing import Dict, List, Union
from PIL import Image
from google.cloud import aiplatform
from google.protobuf import json_format
from google.protobuf.struct_pb2 import Value


def predict_custom_trained_model_sample(
    project: str,
    endpoint_id: str,
    instances: Union[Dict, List[Dict]],
    location: str = "us-central1",
    api_endpoint: str = "us-central1-aiplatform.googleapis.com",
):
    """
    `instances` can be either single instance of type dict or a list
    of instances.
    """
    # The AI Platform services require regional API endpoints.
    client_options = {"api_endpoint": api_endpoint}
    # Initialize client that will be used to create and send requests.
    # This client only needs to be created once, and can be reused for multiple requests.
    client = aiplatform.gapic.PredictionServiceClient(client_options=client_options)
    # The format of each instance should conform to the deployed model's prediction input schema.
    instances = instances if type(instances) == list else [instances]
    instances = [
        json_format.ParseDict(instance_dict, Value()) for instance_dict in instances
    ]
    parameters_dict = {}
    parameters = json_format.ParseDict(parameters_dict, Value())
    endpoint = client.endpoint_path(
        project=project, location=location, endpoint=endpoint_id
    )

    response = client.predict(
        endpoint=endpoint, instances=instances, parameters=parameters
    )
 
    # The predictions are a google.protobuf.Value representation of the model's predictions.
    predictions = response.predictions
    
    print(predictions)


image_path = sys.argv[1]
img = Image.open(image_path)
x = np.asarray(img).astype(np.uint8)[10:210, 10:210].tolist()
#bytes = tf.io.read_file(image_path)
#b64str = base64.b64encode(bytes.numpy()).decode("utf-8")
'''
pred = predict_custom_trained_model_sample(
    project="540638593393",
    endpoint_id="5712371924621852672",
    location="us-central1",
    instances= {'image': x}
)
'''
'''
pred = predict_custom_trained_model_sample(
    project="540638593393",
    endpoint_id="5712371924621852672",
    location="us-central1",
    instances= {'image': x}
)
'''

pred = predict_custom_trained_model_sample(
    project="540638593393",
    endpoint_id="4555087557875990528",
    location="us-central1",
    instances= {'image': x}
)   
