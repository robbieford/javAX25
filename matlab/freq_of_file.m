function freq_of_file( filename, fig_num )
%UNTITLED Summary of this function goes here
%   Detailed explanation goes here

    [y, Fs, nbits] = wavread(filename);
    
    size = 2^nextpow2(length(y));
    freqs = fft(y, size);
    F = (0:1/size:(size-1)/size);
    plot_freq_responses(F, freqs, Fs, fig_num);
end

