# Image for the featured-titles script (featured.bat): Python, ONNX Runtime on the CPU and the
# ISNet background-removal model. Nothing here runs on the TV.
FROM python:3.12-slim-bookworm

RUN pip install --no-cache-dir onnxruntime==1.20.1 numpy==2.1.3 pillow==11.0.0

# ISNet general-use matting model, published with the rembg project (Apache-2.0).
COPY fetch_model.py /opt/fetch_model.py
RUN python /opt/fetch_model.py \
        https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx \
        60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a \
        /opt/models/isnet-general-use.onnx

ENV MATTE_MODEL=/opt/models/isnet-general-use.onnx
WORKDIR /work
