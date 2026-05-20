# ga4gh-reference-cloud-helm
Helm chart for the GA4GH Reference Cloud

## Usage (Local Development)

* Prerequisites (ensure these are installed on your machine):
  * Docker
  * Minikube
  * Helm
* Ensure your `kubectl` active context is the `minikube` context
* Create a kubernetes secret to enable minikube to pull images from private ECR repository, e.g.
  ```
  TOKEN=$(aws ecr get-login-password --region <your-region>)
  kubectl create secret docker-registry ecr-registry-key \
    --docker-server=<aws_account_id>.dkr.ecr.<region>.amazonaws.com \
    --docker-username=AWS \
    --docker-password=$TOKEN \
    --namespace=<your-namespace>
  ```
* Run `helm install refcloud charts/app` to install the helm chart in your Minikube cluster
* Run the following commands to enable port-forwarding to local services:
  ```
  kubectl port-forward service/passport-ui-node 4455:4455 &
  kubectl port-forward service/kratos 4433:4433 &
  ```
