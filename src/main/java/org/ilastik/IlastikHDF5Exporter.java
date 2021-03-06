package org.ilastik;

import ch.systemsx.cisd.hdf5.hdf5lib.H5D;
import ch.systemsx.cisd.hdf5.hdf5lib.H5F;
import ch.systemsx.cisd.hdf5.hdf5lib.H5P;
import ch.systemsx.cisd.hdf5.hdf5lib.H5S;
import ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;

/**
 * Ilastik HDF5 format exporter for ImageJ
 *
 * TODO: make this class implement io.scif.Writer
 */
public class IlastikHDF5Exporter {

    protected String filename;
    protected int file_id;
    private long[] maxdims = { HDF5Constants.H5S_UNLIMITED,
            HDF5Constants.H5S_UNLIMITED,
            HDF5Constants.H5S_UNLIMITED,
            HDF5Constants.H5S_UNLIMITED,
            HDF5Constants.H5S_UNLIMITED };

    private static int FRAMES = 0;
    private static int COLUMNS = 1;
    private static int ROWS = 2;
    private static int LEVS = 3;
    private static int CHANNELS = 4;
    private static int RED = 0;
    private static int GREEN = 1;
    private static int BLUE = 2;
    private static int IJ_RED = 1;
    private static int IJ_GREEN = 2;
    private static int IJ_BLUE = 3;

    /**
     * Initialize HDF5 exporter
     * Try to open file first. If it does not exist, create a new file.
     *
     * @param name HDF5 file name
     */
    public IlastikHDF5Exporter(String name) {
        file_id = -1;
        if (! name.equals("")) {
            try {
                open(name);
            } catch (Exception e) {
                create(name);
            }

        }
    }

    /**
     * Open existing HDF5 file for writing
     *
     * @return File handle when successful
     */
    public int open() {
        if (file_id < 0) {
            file_id = H5F.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
        } else {

            return -1;
        }

        return file_id;
    }

    /**
     * Set HDF5 file name and open it for writing
     *
     * @param name HDF5 file name
     * @return File handle when successful
     */
    public int open(String name) {
        if (file_id < 0) {
            filename = name;
            open();
        } else {

            return -1;
        }

        return file_id;
    }

    /**
     * Create HDF5 file for writing
     *
     * @return File handle when successful
     */
    public int create() {
        if (file_id < 0) {
            file_id = H5F.H5Fcreate(filename,
                    HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
        } else {

            return -1;
        }

        return file_id;
    }

    /**
     * Create HDF5 file for writing
     *
     * @param name HDF5 file name
     * @return File handle when successful
     */
    public int create(String name) {
        if (file_id < 0) {
            filename = name;
            create();
        } else {

            return -1;
        }

        return file_id;
    }

    /**
     * Save image data to HDF5 file
     *
     * @param image Image to export
     */
    public void export(ImagePlus image) {
        export(image, 0, "exported_data");
    }

    /**
     * Save image data to HDF5 file
     *
     * @param image Image to export
     * @param compression Compression setting
     */
    public void export(ImagePlus image, int compression) {
        export(image, compression, "exported_data");
    }

    /**
     * Save image data to HDF5 file
     *
     * @param image Image to export
     * @param datasetname Dataset name to save image under
     */
    public void export(ImagePlus image, String datasetname) {
        export(image, 0, datasetname);
    }

    /**
     * Save image data to HDF5 file
     *
     * @param image Image to export
     * @param compression Compression setting
     * @param datasetname Dataset name to save image under
     */
    public void export(ImagePlus image, int compression, String datasetname) {

        int[] img_dims = getImageDims(image);
        long[] chunk_dims = {1, img_dims[COLUMNS]/8, img_dims[ROWS]/8, img_dims[LEVS], img_dims[CHANNELS]};
        int dcpl_id = H5P.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);

        H5P.H5Pset_chunk(dcpl_id, 5, chunk_dims);
        H5P.H5Pset_deflate(dcpl_id, compression);

        if (image.getType() == ImagePlus.COLOR_RGB) {
            exportRGB(image.getStack(), img_dims, datasetname, dcpl_id);
        } else {
            exportStack(image.getStack(), img_dims, datasetname, dcpl_id);
        }

        H5P.H5Pclose(dcpl_id);
    }

    /**
     * Close HDF5 file
     *
     * @return Non-negative value if successful
     */
    public int close() {
        if (file_id >= 0) {
            H5F.H5Fclose(file_id);
            file_id = -1;

            return 0;
        } else {

            return -1;
        }
    }

    /**
     * Save RGB image stack to HDF5 dataset
     *
     * @param stack RGB stack to save
     * @param img_dims Image dimensions
     * @param datasetname Dataset name
     * @param dcpl_id Property list ID
     */
    private void exportRGB(ImageStack stack, int[] img_dims, String datasetname, int dcpl_id) {

        long[] long_dims = {img_dims[FRAMES], img_dims[COLUMNS], img_dims[ROWS], img_dims[LEVS], 3};
        long[] ini_dims = {1, img_dims[COLUMNS], img_dims[ROWS], 1, 3};

        int dataspace_id = H5S.H5Screate_simple(5, ini_dims, maxdims);
        int dataset_id = createDataset(file_id, dataspace_id, datasetname, dcpl_id, HDF5Constants.H5T_NATIVE_UINT8);
        byte[][] pixels;

        for (int t = 0; t <= img_dims[FRAMES]; t++) {
            for (int z = 0; z < img_dims[LEVS]; z++) {

                int stackIndex = t * img_dims[CHANNELS] * img_dims[LEVS] + z * img_dims[CHANNELS] + 1;
                ColorProcessor cp = (ColorProcessor) (stack.getProcessor(stackIndex));

                byte[] red = cp.getChannel(IJ_RED);
                byte[] green = cp.getChannel(IJ_GREEN);
                byte[] blue = cp.getChannel(IJ_BLUE);

                pixels = new byte[3][red.length];

                for (int y = 0; y < img_dims[ROWS]; y++) {
                    for (int x = 0; x < img_dims[COLUMNS]; x++) {
                        pixels[RED][y + x * (img_dims[ROWS])] = red[x + y * (img_dims[COLUMNS])];
                        pixels[BLUE][y + x * (img_dims[ROWS])] = blue[x + y * (img_dims[COLUMNS])];
                        pixels[GREEN][y + x * (img_dims[ROWS])] = green[x + y * (img_dims[COLUMNS])];
                    }
                }

                if (dataspace_id >= 0) {
                    H5S.H5Sclose(dataspace_id);
                }

                if (z == 0) {
                    if (dataset_id >= 0) {
                        H5D.H5Dset_extent(dataset_id, long_dims);
                    }
                }

                for (int c = 0; c < 3; c++) {
                    long[] start = {t, 0, 0, z, c};
                    long[] count = {0, img_dims[COLUMNS], img_dims[ROWS], 0, c};
                    dataspace_id = writeHyperslab(dataspace_id, dataset_id, ini_dims, start, count, pixels[c]);
                }
            }
        }

        closeDataset(dataspace_id, dataset_id);
    }

    /**
     * Save image stack to HDF5 dataset
     *
     * @param stack Stack to save
     * @param img_dims Image dimensions
     * @param datasetname Dataset name
     * @param dcpl_id Property list ID
     */
    private void exportStack(ImageStack stack, int[] img_dims, String datasetname, int dcpl_id) {

        long[] long_dims = {img_dims[FRAMES], img_dims[COLUMNS], img_dims[ROWS], img_dims[LEVS], img_dims[CHANNELS]};
        long[] ini_dims = {1, img_dims[COLUMNS], img_dims[ROWS], 1, 1};

        int dataspace_id = H5S.H5Screate_simple(5, ini_dims, maxdims);

        int h5type = -1;
        switch(stack.getBitDepth()) {
            case 8:
                h5type = HDF5Constants.H5T_NATIVE_UINT8;
                break;
            case 16:
                h5type = HDF5Constants.H5T_NATIVE_UINT16;
                break;
            case 32:
                h5type = HDF5Constants.H5T_NATIVE_FLOAT;
                break;
        }

        int t = 0;
        int z = 0;
        int c = 0;
        int dataset_id = createDataset(file_id, dataspace_id, datasetname, dcpl_id, h5type);

        for (int i = 1; i <= stack.getSize(); i++) {
            Object pixels = getPixels(stack, i, img_dims[ROWS], img_dims[COLUMNS]);

            if (i == 1) {
                if (dataset_id >= 0)
                    write(dataset_id, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, pixels);
                if (dataspace_id >= 0)
                    H5S.H5Sclose(dataspace_id);
            } else {
                if (i == 2) {
                    H5D.H5Dset_extent(dataset_id, long_dims);
                }
                long[] start = {t, 0, 0, z, c};
                dataspace_id = writeHyperslab(dataspace_id, dataset_id, ini_dims, start, ini_dims, pixels);
            }
            c += 1;

            if ((i % (img_dims[CHANNELS])) == 0) {
                c = 0;
                z += 1;
            }

            if ((i % (img_dims[LEVS] * img_dims[CHANNELS])) == 0) {
                t += 1;
                z = 0;
            }
        }

        closeDataset(dataspace_id, dataset_id);
    }

    /**
     * Get image dimensions
     *
     * @param image Image to process
     * @return
     */
    private int[] getImageDims(ImagePlus image) {

        return new int[]{image.getNFrames(), image.getWidth(),
                image.getHeight(), image.getNSlices(), image.getNChannels()};
    }

    /**
     * Create HDF5 dataset
     *
     * @param file_id File to save dataset to
     * @param dataspace_id Dataspace ID
     * @param datasetname Dataset name
     * @param dcpl_id Property list ID
     * @param h5type HDF5 data type
     * @return Dataset ID when successful
     */
    private int createDataset(int file_id, int dataspace_id, String datasetname, int dcpl_id, int h5type) {

        int dataset_id = -1;

        if ((file_id >= 0) && (dataspace_id >= 0)) {
            dataset_id = H5D.H5Dcreate(file_id, datasetname, h5type, dataspace_id,
                    HDF5Constants.H5P_DEFAULT, dcpl_id, HDF5Constants.H5P_DEFAULT);
        }

        return dataset_id;
    }

    /**
     * Retrieve pixel data from image slice
     *
     * @param stack Stack to retrieve slice from
     * @param i Slice number
     * @param nRows Slice height
     * @param nCols Slice width
     * @return Primitive type pixel array
     */
    private Object getPixels(ImageStack stack, int i, int nRows, int nCols) {

        Object data = stack.getPixels(i);

        if (data instanceof byte[]) {
            byte[] pixels = (byte[]) data;
            byte[] pixels_target = new byte[pixels.length];
            for (int y = 0; y < nRows; y++) {
                for(int x = 0; x < nCols; x++) {
                    pixels_target[y + x * nRows] = pixels[x + y * nCols];
                }
            }
            return pixels_target;
        } else if (data instanceof short[]) {
            short[] pixels = (short[]) data;
            short[] pixels_target = new short[pixels.length];
            for (int y = 0; y < nRows; y++) {
                for(int x = 0; x < nCols; x++) {
                    pixels_target[y + x * nRows] = pixels[x + y * nCols];
                }
            }
            return pixels_target;
        } else if (data instanceof int[]) {
            int[] pixels = (int[]) data;
            int[] pixels_target = new int[pixels.length];
            for (int y = 0; y < nRows; y++) {
                for(int x = 0; x < nCols; x++) {
                    pixels_target[y + x * nRows] = pixels[x + y * nCols];
                }
            }
            return pixels_target;
        } else if (data instanceof float[]) {
            float[] pixels = (float[]) data;
            float[] pixels_target = new float[pixels.length];
            for (int y = 0; y < nRows; y++) {
                for(int x = 0; x < nCols; x++) {
                    pixels_target[y + x * nRows] = pixels[x + y * nCols];
                }
            }
            return pixels_target;
        }

        return null;
    }

    /**
     * Write data to HDF5 hyperslab
     *
     * @param dataspace_id Dataspace ID
     * @param dataset_id Dataset ID
     * @param ini_dims Dataspace dimensions
     * @param start Hyperslab origin
     * @param count Number of blocks to select
     * @param data Data to write
     * @return Dataspace ID when successful
     */
    private int writeHyperslab(int dataspace_id, int dataset_id, long[] ini_dims,
                               long[] start, long[] count, Object data) {
        if (dataspace_id >= 0) {
            dataspace_id = H5D.H5Dget_space(dataset_id);
            H5S.H5Sselect_hyperslab(dataspace_id, HDF5Constants.H5S_SELECT_SET, start, null, count, null);
            int memspace = H5S.H5Screate_simple(5, ini_dims, null);
            if (dataset_id >= 0) {
                write(dataset_id, memspace, dataspace_id, HDF5Constants.H5P_DEFAULT, data);
            }
        }

        return dataspace_id;
    }

    /**
     * Write data to HDF5 dataset
     * @param dataset_id Dataset ID
     * @param mem_space_id Mem space ID
     * @param file_space_id File space ID
     * @param xfer_plist_id Data transfer properties
     * @param data Data to write
     */
    private void write(int dataset_id, int mem_space_id, int file_space_id, int xfer_plist_id, Object data) {

        if (data instanceof byte[]) {
            H5D.H5Dwrite(dataset_id, HDF5Constants.H5T_NATIVE_UINT8, mem_space_id,
                    file_space_id, xfer_plist_id, (byte[]) data);
        } else if (data instanceof short[]) {
            H5D.H5Dwrite(dataset_id, HDF5Constants.H5T_NATIVE_UINT16, mem_space_id,
                    file_space_id, xfer_plist_id, (short[]) data);
        } else if (data instanceof int[]) {
            H5D.H5Dwrite(dataset_id, HDF5Constants.H5T_NATIVE_UINT32, mem_space_id,
                    file_space_id, xfer_plist_id, (int[]) data);
        } else if (data instanceof float[]) {
            H5D.H5Dwrite(dataset_id, HDF5Constants.H5T_NATIVE_FLOAT, mem_space_id,
                    file_space_id, xfer_plist_id, (float[]) data);
        }
    }

    /**
     * Close HDF5 dataspace and dataset
     *
     * @param dataspace_id Dataspace ID
     * @param dataset_id Dataset ID
     */
    private void closeDataset(int dataspace_id, int dataset_id)
    {
        if (dataspace_id >= 0) {
            H5S.H5Sclose(dataspace_id);
        }

        if (dataset_id >= 0) {
            H5D.H5Dclose(dataset_id);
        }
    }
}
